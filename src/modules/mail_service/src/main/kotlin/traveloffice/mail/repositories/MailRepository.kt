package traveloffice.mail.repositories

import com.amazonaws.HttpMethod
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.document.ItemUtils
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import tech.blueglacier.email.Attachment
import tech.blueglacier.email.HtmlEmailBody
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.DEFAULT_NUMBER_OF_MAILS_TO_FETCH
import traveloffice.mail.common.Constant.Companion.ENCRYPT_SECRET_KEY
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_BUCKET
import traveloffice.mail.common.Constant.Companion.MAIL_CDN
import traveloffice.mail.common.Constant.Companion.MAIL_NEW_EMAIL_RECEIVED
import traveloffice.mail.common.Constant.Companion.MAIL_SYNC_EMAIL_FAILED
import traveloffice.mail.common.Constant.Companion.MESSAGE_INDEX
import traveloffice.mail.common.Constant.Companion.S3_TEMPORARY_UPLOAD_FOLDER
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.common.Constant.Companion.TESTING_MAIL_CONTEXT
import traveloffice.mail.common.Constant.Companion.TESTING_MAIL_SUBJECT
import traveloffice.mail.common.Constant.Companion.USER_INDEX
import traveloffice.mail.entities.*
import traveloffice.mail.exceptions.MailAccountWasLinked
import traveloffice.mail.exceptions.UnSupportedInboxType
import traveloffice.mail.extensions.decrypt
import traveloffice.mail.extensions.encrypt
import traveloffice.mail.providers.AppSyncProvider
import traveloffice.mail.providers.MailProviderFactory
import traveloffice.mail.providers.S3Provider
import traveloffice.mail.providers.SMTPProvider
import traveloffice.mail.services.SecretManagerService
import java.net.URLEncoder
import java.util.*
import javax.mail.Flags
import javax.mail.Message
import javax.mail.internet.InternetAddress

@Repository
@DependsOn(value = ["SecretManager"])
class MailRepository {
    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

    @Autowired
    lateinit var mailProviderFactory: MailProviderFactory

    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var appSyncProvider: AppSyncProvider

    @Autowired
    lateinit var s3Provider: S3Provider

    @Autowired
    lateinit var smtpProvider: SMTPProvider

    var logger: Logger = LoggerFactory.getLogger(MailRepository::class.java)


    /**
     * This method perform get new message from the last sync
     *
     * The method perform following steps:
     * Step 1: Get the mailBox inbound Type, currently only supported IMAP and POP3
     * Step 2: Get the last mailbox synced time
     * Step 3: Get the message from the provider
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @return List of new message from the last sync
     * @exception UnSupportedInboxType
     */
    fun getLastMailBoxSync(mailAccount: MailAccount, mailBox: MailBox): List<Message> {
        logger.info("getLastMailBoxSync MailBox[${mailBox.name}] with MailAccount [${mailAccount.name}]")
        val mailProvider = mailProviderFactory.makeProvider(mailAccount.protocol)
        val lastMailboxSync = mailBox.lastMailboxSync
        val mailBoxName = mailBox.name
        return if (lastMailboxSync == null) {
            // Get all message for the first sync
            mailProvider.getMessages(mailAccount, mailBoxName)
        } else {
            val lastSync = Date(lastMailboxSync)
            // Get all message from the last sync to now
            mailProvider.getMessages(mailAccount, mailBoxName, lastSync, null)
        }
    }

    /**
     * This method perform get a mail information from the message.
     *
     * The method perform following steps:
     * Step 1: Get the actual provider of the message.
     * Step 2: Use Mime4j parse the message.
     * Step 3: Get the brief content of the message.
     * Step 4: Create the mail message information.
     *
     * @param mailAccount: The mail account information.
     * @param message: The message information.
     * @return Mail message information which parsed from message.
     * @exception
     */
    fun toEmail(mailAccount: MailAccount, message: Message): MailMessage {
        // Get the actual provider of the message
        val mailProvider = mailProviderFactory.makeProvider(mailAccount.protocol)
        // Use Mime4j parse the message
        val email = mailProvider.parseMessage(message)

        // Get the brief content of the message
        var briefContent = ""
        if (email.plainTextEmailBody != null) {
            val emailContentStream = email.plainTextEmailBody.`is`
            if (emailContentStream.available() > 0) {
                val defaultBriefContentSize = this.secretManagerService.getEnv("MAX_MESSAGE_BRIEF_CONTENT_SIZE").toInt()
                briefContent = if (defaultBriefContentSize < emailContentStream.available()) {
                    String(emailContentStream.readNBytes(defaultBriefContentSize))
                } else {
                    String(emailContentStream.readAllBytes())
                }
            }
        }

        // Create the mail message information
        val mailMessage = MailMessage()
        mailMessage.messageId = mailProvider.getMessageId(message)
        mailMessage.briefContent = briefContent
        mailMessage.htmlContent = this.storeHtmlContent(
            mailAccount, mailMessage.messageId, email.htmlEmailBody as HtmlEmailBody
        )
        mailMessage.receivedDate = if (message.receivedDate != null) message.receivedDate.time else null
        mailMessage.sentDate = if (message.sentDate != null) message.sentDate.time else null
        mailMessage.subject = email.emailSubject ?: ""
        mailMessage.from = getAddressFromHeader(email.fromEmailHeaderValue)
        mailMessage.to = getAddressFromHeader(email.toEmailHeaderValue)
        mailMessage.cc = getAddressFromHeader(email.ccEmailHeaderValue)
        mailMessage.bcc = getAddressFromHeader(email.header.getField("Bcc")?.body)
        mailMessage.inReplyTo = mailProvider.getInRelyTo(message)
        mailMessage.attachments = getAttachmentsFromEmail(
            mailAccount, mailMessage.messageId, email.attachments
        )
        mailMessage.size = email.emailSize
        return mailMessage
    }

    /**
     * This method perform to actually address information from header value
     *
     * @param headerValue: The message header value which contain address information.
     *
     * @return The list of EmailAddress.
     * @exception
     */
    private fun getAddressFromHeader(headerValue: String?): List<EmailAddress> {
        if (headerValue.isNullOrEmpty()) return emptyList()
        return InternetAddress.parse(headerValue).map {
            val emailAddress = EmailAddress()
            emailAddress.address = it.address
            emailAddress.personal = it.personal ?: null
            return@map emailAddress
        }
    }

    /**
     * This method to validate mail account information
     *
     * @param mailAccount The mail account information
     * @return void
     */
    fun validateMailAccount(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser) {
        logger.info("Validate information of MailAccount [$mailAccount]")
        val mailProvider = mailProviderFactory.makeProvider(mailAccount.protocol)
        // Validate existing email
        this.checkExistingAccount(mailAccount, authenticatedUser)
        // Validate incoming server
        mailProvider.validateIncomingServer(mailAccount)
        // Validate outgoing server
        val mailMessage = MailMessage()
        val emailAddress = EmailAddress.Builder().setEmailAddress(mailAccount.email).build()
        mailMessage.to = listOf(emailAddress)
        mailMessage.from = listOf(emailAddress)
        mailMessage.subject = TESTING_MAIL_SUBJECT
        mailMessage.htmlContent = TESTING_MAIL_CONTEXT
        smtpProvider.sendMail(mailAccount, mailMessage)
    }

    /**
     * This method to check the mail account is exist or not
     *
     * @param mailAccount The mail account information
     * @param authenticatedUser The authenticated user information
     * @exception MailAccountWasLinked
     */
    private fun checkExistingAccount(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser) {
        val pk = "$TENANT_INDEX#${authenticatedUser.tenant}#$USER_INDEX#${authenticatedUser.username}"
        val sk = "$MAIL_ACCOUNT_INDEX#${mailAccount.email}"
        val result = this.dynamoDBMapper.load(
            MailAccount::class.java, pk, sk
        )
        if (result != null) {
            throw MailAccountWasLinked("Your account is already linked")
        }
    }

    /**
     * This method perform to get the attachments from the message attachments
     *
     * @param mailAccount: The mail account information.
     * @param messageId: The message Id.
     * @param attachments: The list email attachment.
     *
     * @return The list of MailAttachment.
     * @exception
     */
    private fun getAttachmentsFromEmail(
        mailAccount: MailAccount, messageId: String, attachments: List<Attachment>
    ): List<MailAttachment>? {
        if (attachments.isEmpty()) return null
        return attachments.map {
            val attachment = MailAttachment()
            attachment.mimeType = it.bd.mimeType
            attachment.name = it.attachmentName
            attachment.size = it.attachmentSize
            attachment.encoding = it.bd.charset ?: ""
            attachment.inputStream = it.`is`
            attachment.path = this.storeAttachment(mailAccount, messageId, attachment)
            return@map attachment
        }.toList()
    }

    /**
     * This method perform to get the tenant mail account
     *
     * @param tenant: The tenant identify.
     * @param username: The username.
     * @param account: The mail account name stand for email.
     *
     * @return The email account information.
     * @exception
     */
    fun getMailAccount(tenant: String, username: String, account: String): MailAccount? {
        val mailAccount = this.dynamoDBMapper.load(
            MailAccount::class.java,
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}",
            "${MAIL_ACCOUNT_INDEX}#${account}"
        )
        //Decrypt password
        val encryptionKey = this.secretManagerService.getAWSSecretValue(ENCRYPT_SECRET_KEY)
        mailAccount.inboundPassword = mailAccount.inboundPassword.decrypt(encryptionKey)
        mailAccount.outboundPassword = mailAccount.outboundPassword.decrypt(encryptionKey)
        mailAccount.tenant = tenant
        mailAccount.username = username
        return mailAccount
    }

    /**
     * This method perform to get the tenant mailbox
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox name.
     *
     * @return The mailbox information.
     * @exception
     */
    fun getMailBox(mailAccount: MailAccount, mailBox: String): MailBox? {
        return this.dynamoDBMapper.load(
            MailBox::class.java,
            "${TENANT_INDEX}#${mailAccount.tenant}#" + "${USER_INDEX}#${mailAccount.username}#" + "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" + MAILBOX_INDEX,
            "$MAILBOX_INDEX#${mailBox}"
        )
    }

    /**
     * Get mail boxes for given [mail] which related to [tenant] and [username]
     *
     * Returns [List] of [MailBox] object
     */
    fun getMailBoxes(tenant: String, username: String, mail: String): List<MailBox> {
        // Pk, sk
        val pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}"
        val sk = "$MAILBOX_INDEX#"
        // Build expression attribute values. Then, make query expression
        val eav: Map<String, AttributeValue> = hashMapOf(
            ":pk" to AttributeValue().withS(pk),
            ":sk" to AttributeValue().withS(sk)
        )
        val queryExpression = DynamoDBQueryExpression<MailBox>()
            .withKeyConditionExpression("pk = :pk and begins_with(sk, :sk)")
            .withExpressionAttributeValues(eav)

        return this.dynamoDBMapper.query(MailBox::class.java, queryExpression)
    }

    /**
     * Get mail message for given [getMailParams] which related to [tenant] and [username]
     *
     * Returns [MailMessage] object
     */
    private fun getMailMessage(
        tenant: String,
        username: String,
        getMailParams: GetMailRequestParams,
    ): MailMessage? {
        return this.dynamoDBMapper.load(
            MailMessage::class.java,
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${getMailParams.email}#${MAILBOX_INDEX}#${getMailParams.mailbox}",
            "$MESSAGE_INDEX#${getMailParams.mailId}"
        )
    }

    /**
     * Get thread mails for given [getMailParams] which related to [tenant] and [username]
     *
     * Returns list of [MailMessage] object
     */
    fun getThreadMails(
        tenant: String,
        username: String,
        getMailParams: GetMailRequestParams,
    ): List<MailMessage> {
        val threadMails = mutableListOf<MailMessage>()
        val mailMessage = this.getMailMessage(tenant, username, getMailParams)
        if (mailMessage != null) {
            threadMails.add(mailMessage)
            if (mailMessage.inReplyTo != null) {
                val getSubMailRequestParams = getMailParams.copy(mailId = mailMessage.inReplyTo!!)
                val subThreadMails = this.getThreadMails(tenant, username, getSubMailRequestParams)
                threadMails.addAll(subThreadMails)
            }
        }
        return threadMails
    }

    /**
     * Change status of mail message for given [getMailParams] which related to [tenant] and [username]
     * Change from [fromFlags] to [toFlags]
     */
    fun changeMailMessageStatus(
        tenant: String,
        username: String,
        getMailParams: GetMailRequestParams,
        fromFlags: Flags,
        toFlags: Flags
    ) {
        val mailMessage = this.getMailMessage(tenant, username, getMailParams)
        if (mailMessage != null) {
            val mailMessageUpdateRequest = MailMessageUpdateRequest.Builder()
                .withHashKey(mailMessage.pk)
                .withRangeKey(mailMessage.sk)
                .build()

            val flags = mailMessage.flags.clone() as Flags
            flags.remove(fromFlags)
            flags.add(toFlags)
            mailMessageUpdateRequest.flags = flags
            this.updateMailMessage(mailMessageUpdateRequest)
        }
    }

    /**
     * Get mail account for given [username] that belong to [tenant]
     *
     * Returns [List] of [MailAccount] object
     */
    fun getUserMailAccounts(tenant: String, username: String): List<MailAccount> {
        // Pk, sk
        val pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}"
        val sk = "$MAIL_ACCOUNT_INDEX#"
        // Build expression attribute values. Then, make query expression
        val eav: Map<String, AttributeValue> = hashMapOf(
            ":pk" to AttributeValue().withS(pk),
            ":sk" to AttributeValue().withS(sk)
        )
        val queryExpression = DynamoDBQueryExpression<MailAccount>()
            .withKeyConditionExpression("pk = :pk and begins_with(sk, :sk)")
            .withExpressionAttributeValues(eav)

        return this.dynamoDBMapper.query(MailAccount::class.java, queryExpression)
    }

    /**
     * This method perform to change the mailbox sync status
     *
     * @param mailAccount: The mailbox information.
     * @param mailMessage: The status of sync action.
     *
     * @return
     * @exception
     */
    fun storeEmailToDatabase(mailAccount: MailAccount, mailBox: MailBox, mailMessage: MailMessage) {
        mailMessage.pk =
            "${TENANT_INDEX}#${mailAccount.tenant}#" + "${USER_INDEX}#${mailAccount.username}#" + "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" + "${MAILBOX_INDEX}#${mailBox.name}"
        mailMessage.sk = "${MESSAGE_INDEX}#${mailMessage.messageId}"
        (mailMessage.receivedDate ?: mailMessage.sentDate)?.let {
            mailMessage.lsi1 = "$MAILBOX_INDEX#${mailBox.name}#${it}"
        }
        return this.dynamoDBMapper.save(mailMessage)
    }

    /**
     * This method perform to save attachment content to S3
     *
     * @param mailAccount: The mailbox information.
     * @param messageId: The message Id.
     * @param attachment: The attachment information
     *
     * @return The path of attachment which saved to the local storage
     * @exception
     */
    private fun storeAttachment(
        mailAccount: MailAccount,
        messageId: String,
        attachment: MailAttachment,
    ): String {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val tenantKeyPrefix = "${mailAccount.tenant}/${mailAccount.username}/${mailAccount.email}/${messageId}"
        val mailObjectKey = "${tenantKeyPrefix}/${attachment.name}"
        val metadata = ObjectMetadata()
        metadata.contentType = attachment.mimeType
        metadata.contentLength = attachment.size.toLong()
        metadata.contentEncoding = attachment.encoding
        val putObjectRequest = PutObjectRequest(mailBucket, mailObjectKey, attachment.inputStream, metadata)
        this.s3Provider.putObject(putObjectRequest)
        return URLEncoder.encode(mailObjectKey, "UTF-8")
    }


    /**
     * This method perform to save email body as html content to S3
     *
     * @param mailAccount: The mail account information.
     * @param messageId: The message Id.
     * @param htmlEmailBody: The email body as html content
     *
     * @return The path of html email body which saved to the local storage
     * @exception
     */
    private fun storeHtmlContent(
        mailAccount: MailAccount, messageId: String, htmlEmailBody: HtmlEmailBody
    ): String {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val tenantKeyPrefix = "${mailAccount.tenant}/${mailAccount.username}/${mailAccount.email}/${messageId}"
        val htmlObjectKey = "${tenantKeyPrefix}/htmlContent/index.html"
        val metadata = ObjectMetadata()
        metadata.contentType = "text/html"
        metadata.contentEncoding = htmlEmailBody.bd.charset
        metadata.contentLength = htmlEmailBody.`is`.available().toLong()
        val putObjectRequest = PutObjectRequest(mailBucket, htmlObjectKey, htmlEmailBody.`is`, metadata)
        this.s3Provider.putObject(putObjectRequest)
        return URLEncoder.encode(htmlObjectKey, "UTF-8")
    }

    /**
     * This method perform to change the mailbox sync status
     *
     * @param mailBox: The mailbox information.
     * @param status: The status of sync action.
     *
     * @return
     * @exception
     */
    fun changeMailBoxStatus(mailBox: MailBox, status: MailBoxSyncStatus) {
        val box = MailBox()
        box.pk = mailBox.pk
        box.sk = mailBox.sk
        box.syncStatus = status.toString()
        this.updateMailBox(box)
    }

    /**
     * This method perform to update the last sync time of mailbox
     *
     * @param mailBox: The mailbox information.
     * @param syncTime: The current sync time to be updated.
     *
     * @return The path of html email body which saved to the local storage
     * @exception
     */
    fun updateMailBoxLastSyncTime(mailBox: MailBox, syncTime: Date?) {
        val box = MailBox()
        box.pk = mailBox.pk
        box.sk = mailBox.sk
        box.lastMailboxSync = syncTime?.time
        this.updateMailBox(box)
    }

    /**
     * This method perform to update the mailbox size
     *
     * @param mailBox: The mailbox information.
     * @param size: The change size to be updated
     *  + for increase size,
     *  - for decrease size.
     * @param count: The change count to be updated
     *  + for increase,
     *  - for decrease.
     * @param unread: The change unread to be updated
     *  + for increase,
     *  - for decrease.
     *
     * @return The new storage size of the mailbox
     * @exception
     */
    fun updateMailBoxMetric(mailBox: MailBox, size: Int, count: Int, unread: Int) {
        val mailBoxPk = mailBox.pk
        val mailBoxSk = mailBox.sk
        val currentMailBox = this.dynamoDBMapper.load(MailBox::class.java, mailBoxPk, mailBoxSk)
        val box = MailBox()
        box.pk = mailBoxPk
        box.sk = mailBoxSk
        box.size = (currentMailBox.size ?: 0) + size.toLong()
        box.count = (currentMailBox.count ?: 0) + count
        box.unread = (currentMailBox.unread ?: 0) + unread
        this.updateMailBox(box)
    }

    /**
     * This method perform to update the mailbox information
     *
     * @param mailBox: The mailbox information.
     *
     * @return The new storage size of the mailbox
     * @exception
     */
    private fun updateMailBox(mailBox: MailBox) {
        val config = DynamoDBMapperConfig.builder()
            .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES).build()
        return this.dynamoDBMapper.save(mailBox, config)
    }

    /**
     * This method perform to update the mailbox information
     *
     * @param mailMessageUpdateRequest: The message update information.
     *
     * @return The new storage size of the mailbox
     * @exception
     */
    private fun updateMailMessage(mailMessageUpdateRequest: MailMessageUpdateRequest) {
        val config = DynamoDBMapperConfig.builder()
            .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES).build()
        return this.dynamoDBMapper.save(mailMessageUpdateRequest, config)
    }

    /**
     * This method perform to notification new email received
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox information.
     * @param mailMessage: The mail message information to be notified.
     *
     * @return
     * @exception
     */
    fun notifyNewEmailReceived(
        mailAccount: MailAccount,
        mailBox: MailBox,
        mailMessage: MailMessage,
        firstSync: Boolean
    ) {
        val notificationType = MAIL_NEW_EMAIL_RECEIVED
        val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
        val data = this.dynamoDBMapper.load(
            MailMessage::class.java, mailMessage.pk, mailMessage.sk
        ).toJsonWithCDN(mailCDN).toMutableMap()
        data["firstSync"] = firstSync

        this.appSyncProvider.executeSendNotification(
            notificationType, Gson().toJson(data), Date().time, mailAccount.tenant, mailAccount.username
        )
    }

    /**
     * This method perform to notification sync email failed
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox information.
     * @param message: The sync email error message.
     *
     * @return
     * @exception
     */
    fun notifySyncEmailError(mailAccount: MailAccount, mailBox: MailBox, message: String) {
        val notificationType = MAIL_SYNC_EMAIL_FAILED
        val data = mapOf(
            "mailbox" to mailBox.name,
            "message" to message,
        )

        this.appSyncProvider.executeSendNotification(
            notificationType, Gson().toJson(data), Date().time, mailAccount.tenant, mailAccount.username
        )
    }

    /**
     * This method to save mail account to dynamo db
     *
     * @param mailAccount The mail account information
     * @param authenticatedUser The authenticated user information
     * @param encryptionKey The secret key for encrypting inbound/outbound password
     */
    fun linkEmail(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser, encryptionKey: String) = try {
        val userPk = "$TENANT_INDEX#${authenticatedUser.tenant}#$USER_INDEX#${authenticatedUser.username}"
        // Generate pk, sk for dynamo db
        mailAccount.pk = userPk
        mailAccount.sk = "$MAIL_ACCOUNT_INDEX#${mailAccount.email}"
        // Save default mailbox to dynamodb
        for (name in Constant.DEFAULT_MAILBOX) {
            val mailBox = MailBox()
            mailBox.pk = "$userPk#$MAIL_ACCOUNT_INDEX#${mailAccount.email}#$MAILBOX_INDEX"
            mailBox.sk = "$MAILBOX_INDEX#$name"
            mailBox.count = 0
            mailBox.unread = 0
            this.dynamoDBMapper.save(mailBox)
        }
        // Encrypt mail password
        mailAccount.inboundPassword = mailAccount.inboundPassword.encrypt(encryptionKey)
        mailAccount.outboundPassword = mailAccount.outboundPassword.encrypt(encryptionKey)
        // Set tenant, username for mail account
        mailAccount.tenant = authenticatedUser.tenant
        mailAccount.username = authenticatedUser.username
        // Save mail account information to dynamodb
        this.dynamoDBMapper.save(mailAccount)
        logger.info("Save mail account ${mailAccount.email} to dynamodb successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw Exception("Failed to link mail account ${mailAccount.email}")
    }

    /**
     * This method performs generate s3 presigned url
     *
     * @params fileName The name of file that will be uploaded to s3
     * @return PresignedUrlResponsePayload object
     */
    fun generatePresignedUrl(fileName: String): PresignedUrlResponsePayload {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val objectKey = "${S3_TEMPORARY_UPLOAD_FOLDER}/${UUID.randomUUID()}/${fileName}"
        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(mailBucket, objectKey, HttpMethod.PUT)
        val url = this.s3Provider.generatePresignedUrl(generatePresignedUrlRequest)
        return PresignedUrlResponsePayload(url.toString(), url.path)
    }

    /**
     * Get [mailBox] mails of [mail] address for given [username] that related to [tenant]
     *
     * Returns [List] of [MailMessage] object
     */
    fun getMailsInsideMailBox(
        tenant: String,
        username: String,
        mail: String,
        mailBox: String,
        lastEvaluatedKeyString: String? = null
    ): Pair<String, MutableList<MailMessage>> {
        // Pk, sk
        val uppercasedMailBox = mailBox.uppercase()
        val pk =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#${uppercasedMailBox}"
        val sk = "$MAILBOX_INDEX#${uppercasedMailBox}"
        // Build expression attribute values. Then, make query expression
        val eav: Map<String, AttributeValue> = hashMapOf(
            ":pk" to AttributeValue().withS(pk),
            ":lsi1" to AttributeValue().withS(sk)
        )
        var lastEvaluatedKey: Map<String, AttributeValue>? = null
        if (lastEvaluatedKeyString != null) {
            lastEvaluatedKey =
                ItemUtils.fromSimpleMap(
                    Gson().fromJson<Map<String, AttributeValue>>(
                        lastEvaluatedKeyString,
                        MutableMap::class.java
                    )
                )
        }
        val queryExpression = DynamoDBQueryExpression<MailMessage>()
            .withIndexName("Index1") // Index for sorting
            .withKeyConditionExpression("pk = :pk and begins_with(lsi1, :lsi1)")
            .withScanIndexForward(false) // DESC sk
            .withExclusiveStartKey(lastEvaluatedKey)
            .withLimit(DEFAULT_NUMBER_OF_MAILS_TO_FETCH) // Limit number of mails returned
            .withExpressionAttributeValues(eav)

        val result = this.dynamoDBMapper.queryPage(MailMessage::class.java, queryExpression)
        return Pair(Gson().toJson(ItemUtils.toSimpleMapValue<Any>(result.lastEvaluatedKey)), result.results)
    }
}
