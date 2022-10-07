package traveloffice.mail.repositories

import com.amazonaws.HttpMethod
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.KeyPair
import com.amazonaws.services.dynamodbv2.document.ItemUtils
import com.amazonaws.services.dynamodbv2.model.AttributeAction
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import com.amazonaws.services.s3.model.*
import com.google.gson.Gson
import org.apache.commons.collections.ListUtils
import org.apache.commons.io.IOUtils
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import tech.blueglacier.email.Attachment
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.DEFAULT_ENCODING
import traveloffice.mail.common.Constant.Companion.DEFAULT_NUMBER_OF_MAILS_TO_FETCH
import traveloffice.mail.common.Constant.Companion.DEFAULT_NUMBER_OF_MAILS_TO_SYNC
import traveloffice.mail.common.Constant.Companion.DEFAULT_PRESIGNED_URL_EXPIRATION
import traveloffice.mail.common.Constant.Companion.ENCRYPT_SECRET_KEY
import traveloffice.mail.common.Constant.Companion.FAILED_INDEX
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_BUCKET
import traveloffice.mail.common.Constant.Companion.MAIL_CDN
import traveloffice.mail.common.Constant.Companion.MAIL_NEW_EMAIL_RECEIVED
import traveloffice.mail.common.Constant.Companion.MAX_NUMBER_LAST_SYNC_STATUS_COUNT
import traveloffice.mail.common.Constant.Companion.MESSAGE_INDEX
import traveloffice.mail.common.Constant.Companion.S3_TEMPORARY_UPLOAD_FOLDER
import traveloffice.mail.common.Constant.Companion.SUCCESS_INDEX
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.common.Constant.Companion.TESTING_MAIL_CONTEXT
import traveloffice.mail.common.Constant.Companion.TESTING_MAIL_SUBJECT
import traveloffice.mail.common.Constant.Companion.USER_INDEX
import traveloffice.mail.entities.*
import traveloffice.mail.exceptions.MailAccountWasLinked
import traveloffice.mail.exceptions.S3DownloadFileException
import traveloffice.mail.exceptions.UnSupportedInboxType
import traveloffice.mail.extensions.decrypt
import traveloffice.mail.extensions.encodeToURL
import traveloffice.mail.extensions.encrypt
import traveloffice.mail.providers.AppSyncProvider
import traveloffice.mail.providers.MailProviderFactory
import traveloffice.mail.providers.S3Provider
import traveloffice.mail.providers.SMTPProvider
import traveloffice.mail.services.SecretManagerService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import javax.mail.internet.InternetAddress


@Repository
@DependsOn(value = ["SecretManager"])
class MailRepository {
    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

    @Autowired
    lateinit var dynamoDBClient: AmazonDynamoDB

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
    fun getNewEmailFromLastSync(
        mailAccount: MailAccount,
        mailBox: MailBox,
        lastSyncInfo: List<MailMessageSyncStatus>
    ): List<MailMessage> {
        logger.info("getLastMailBoxSync MailBox[${mailBox.name}] with MailAccount [${mailAccount.name}]")
        val mailProvider = mailProviderFactory.makeProvider(mailAccount.protocol)
        val mailBoxName = mailBox.name
        return if (lastSyncInfo.isEmpty()) {
            // The first sync time, limit the number of message to sync
            mailProvider.getMessages(mailAccount, mailBoxName, DEFAULT_NUMBER_OF_MAILS_TO_SYNC)
        } else {
            val lastSyncFailedInfoUID = this.getSyncInfo(mailAccount, mailBox.name, true).map { it.uid }
            val lastSyncInfoUID = lastSyncInfo.map { it.uid }
            val lastSyncTime = lastSyncInfo.first().syncTime
            mailProvider.getMessages(mailAccount, mailBoxName, lastSyncInfoUID, lastSyncFailedInfoUID, lastSyncTime)
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
     * @param mailMessage: The mail message information.
     * @return Mail message information which parsed from message.
     * @exception
     */
    fun parseMailMessage(mailAccount: MailAccount, mailMessage: MailMessage) {
        val message = mailMessage.raw!!
        logger.info(
            "Start parse message [${message.messageNumber}] " +
                    "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                    "Mail Account [${mailAccount.email}]"
        )
        // Get the actual provider of the message
        val mailProvider = mailProviderFactory.makeProvider(mailAccount.protocol)
        // Use Mime4j parse the message
        val email = mailProvider.parseMessage(message)

        // Create the mail message information
        mailMessage.messageId = mailProvider.getMessageId(message)
        mailMessage.plainTextEmailBody = email.plainTextEmailBody?.`is`
        mailMessage.rawHtmlContent = getHtmlContentFromEmail(email.htmlEmailBody)
        mailMessage.receivedDate = message.receivedDate?.time
        mailMessage.sentDate = message.sentDate?.time
        mailMessage.subject = email.emailSubject ?: ""
        mailMessage.from = getAddressFromHeader(email.fromEmailHeaderValue)
        mailMessage.to = BriefAddress.Builder().withAddresses(getAddressFromHeader(email.toEmailHeaderValue)).build()
        mailMessage.cc = BriefAddress.Builder().withAddresses(getAddressFromHeader(email.ccEmailHeaderValue)).build()
        mailMessage.bcc = BriefAddress.Builder().withAddresses(getAddressFromHeader(email.bccEmailHeaderValue)).build()
        mailMessage.inReplyTo = mailProvider.getInRelyTo(message)
        mailMessage.attachments = getAttachmentsFromEmail(email.attachments)
        mailMessage.size = email.emailSize.toLong()
        logger.info(
            "Parse successfully message [${message.messageNumber}] " +
                    "with messageID [${mailMessage.messageId}] " +
                    "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                    "Mail Account [${mailAccount.email}]"
        )
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
        return InternetAddress.parseHeader(headerValue, false).map {
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
        mailMessage.to = BriefAddress.Builder().withAddresses(listOf(emailAddress)).build()
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
     * @param attachments: The list email attachment.
     *
     * @return The list of MailAttachment.
     * @exception
     */
    private fun getAttachmentsFromEmail(attachments: List<Attachment>): List<MailAttachment>? {
        if (attachments.isEmpty()) return null
        return attachments.map {
            val attachment = MailAttachment()
            attachment.mimeType = it.bd.mimeType
            attachment.name = it.attachmentName
            attachment.size = it.attachmentSize.toLong()
            attachment.encoding = it.bd.charset ?: DEFAULT_ENCODING
            attachment.inputStream = it.`is`
            return@map attachment
        }.toList()
    }

    private fun getHtmlContentFromEmail(htmlEmailBody: Attachment?): MailAttachment? {
        if (htmlEmailBody == null) return null
        return MailAttachment.Builder()
            .withName("index.html")
            .withMimeType("text/html")
            .withEncoding(htmlEmailBody.bd.charset)
            .withSize(htmlEmailBody.attachmentSize.toLong())
            .withInputStream(htmlEmailBody.`is`)
            .build()
    }

    /**
     * This method perform to get the tenant mail account
     *
     * @param tenant: The tenant identify.
     * @param username: The username.
     * @param email: The email address
     *
     * @return The email account information.
     * @exception
     */
    fun getMailAccount(tenant: String, username: String, email: String): MailAccount? {
        val mailAccount = this.dynamoDBMapper.load(
            MailAccount::class.java,
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}",
            "${MAIL_ACCOUNT_INDEX}#${email}"
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
    fun saveEmail(mailAccount: MailAccount, mailBoxName: String, mailMessage: MailMessage) {
        mailMessage.pk =
            "${TENANT_INDEX}#${mailAccount.tenant}#${USER_INDEX}#${mailAccount.username}#${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#${MAILBOX_INDEX}#${mailBoxName}"
        mailMessage.sk = "${MESSAGE_INDEX}#${mailMessage.messageId}"
        mailMessage.lsi1 = "$MAILBOX_INDEX#${mailBoxName}#${mailMessage.sentDate}"

        val rawHtmlContent = mailMessage.rawHtmlContent
        if (mailMessage.htmlContent == "" && rawHtmlContent != null) {
            // Keep the html input stream to get brief content
            val byteArrayOutputStream = ByteArrayOutputStream()
            rawHtmlContent.inputStream?.transferTo(byteArrayOutputStream)
            rawHtmlContent.inputStream = ByteArrayInputStream(byteArrayOutputStream.toByteArray())
            mailMessage.htmlContent = storeHtmlContent(mailAccount, mailMessage.messageId, rawHtmlContent)
            rawHtmlContent.inputStream = ByteArrayInputStream(byteArrayOutputStream.toByteArray())
        }
        mailMessage.attachments = mailMessage.attachments?.map {
            if (it.inputStream != null) {
                it.path = storeAttachment(mailAccount, mailMessage.messageId, it)
            }
            return@map it
        }
        // Get the brief content of the message
        var briefContent = ""
        try {
            val rawHtmlContentIs = mailMessage.rawHtmlContent?.inputStream
            if (rawHtmlContentIs != null) {
                // Parse brief content from html content
                val doc = Jsoup.parse(rawHtmlContentIs, StandardCharsets.UTF_8.toString(), "")
                val defaultBriefContentSize = this.secretManagerService.getEnv("MAX_MESSAGE_BRIEF_CONTENT_SIZE").toInt()
                val stringContent = IOUtils.toString(doc.text().byteInputStream(), StandardCharsets.UTF_8).trim()
                briefContent = stringContent
                    .substring(0, defaultBriefContentSize.coerceAtMost(stringContent.length))
                    .replace("\n", "")
                    .replace("\r", "")
            }
        } catch (e: IOException) {
            logger.error("Cannot get brief content of message [${mailMessage.messageId}]")
        }
        mailMessage.briefContent = briefContent

        // Save the metadata
        val metaData = MailMessageMetaData(
            mailMessage.to.rawAddress,
            mailMessage.cc?.rawAddress,
            mailMessage.bcc?.rawAddress
        )
        mailMessage.metaData = storeMetaData(mailAccount, mailMessage.messageId, metaData)

        return this.dynamoDBMapper.save(mailMessage)
    }

    /**
     * Get all synced information from [mailAccount] and [mailBoxName]
     */
    fun getSyncInfo(mailAccount: MailAccount, mailBoxName: String, failed: Boolean): List<MailMessageSyncStatus> {
        val syncInfoPk = "${TENANT_INDEX}#${mailAccount.tenant}#" +
                "${USER_INDEX}#${mailAccount.username}#" +
                "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" +
                "${MAILBOX_INDEX}#${mailBoxName}"
        val syncInfoSk = if (failed) {
            FAILED_INDEX
        } else {
            SUCCESS_INDEX
        }
        val eav: HashMap<String, AttributeValue> = HashMap<String, AttributeValue>()
        eav[":pk"] = AttributeValue().withS(syncInfoPk)
        eav[":sk"] = AttributeValue().withS(syncInfoSk)

        val queryExpression = DynamoDBQueryExpression<MailMessageSyncStatus>()
            .withKeyConditionExpression("pk = :pk and begins_with(sk, :sk)")
            .withExpressionAttributeValues(eav)
            .withScanIndexForward(false) // DESC sk
        return this.dynamoDBMapper.query(MailMessageSyncStatus::class.java, queryExpression)
    }

    /**
     * Store all synced information [successResult] and [failedResult] from [mailAccount] and [mailBoxName] to Database
     * Do following steps:
     * - Batch remove last sync failed information
     * - Batch load message items
     * - If IMAP save only last synced information
     * - Batch write message items to new partition belongs to database
     */
    fun saveSyncInfo(
        mailAccount: MailAccount,
        mailBoxName: String,
        successResult: List<MailMessageSyncStatus>,
        failedResult: List<MailMessageSyncStatus>,
        lastSyncInfo: List<MailMessageSyncStatus>
    ) {
        // Delete all last sync error
        val failedInfo = this.getSyncInfo(mailAccount, mailBoxName, true)
        this.dynamoDBMapper.batchDelete(failedInfo)

        val newSuccessResult = if (mailAccount.protocol == "imap") {
            if (lastSyncInfo.isEmpty() && successResult.isNotEmpty()) {
                listOf(successResult.first())
            } else if (successResult.isNotEmpty() && (lastSyncInfo.first().uid.toLong() < successResult.first().uid.toLong())
            ) {
                this.dynamoDBMapper.batchDelete(lastSyncInfo)
                listOf(successResult.first())
            } else {
                emptyList()
            }
        } else {
            this.dynamoDBMapper.batchDelete(lastSyncInfo)
            val newList = successResult.toMutableList()
            newList.addAll(lastSyncInfo)
            newList.sortedByDescending { it.syncTime }.stream().limit(MAX_NUMBER_LAST_SYNC_STATUS_COUNT).toList()
        }

        val syncInfoPk = "${TENANT_INDEX}#${mailAccount.tenant}#" +
                "${USER_INDEX}#${mailAccount.username}#" +
                "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" +
                "${MAILBOX_INDEX}#${mailBoxName}"

        val newSuccessSyncInfo = newSuccessResult.map { info ->
            info.pk = syncInfoPk
            info.sk = "${SUCCESS_INDEX}#${info.syncTime}"
            return@map info
        }
        val newFailedSyncInfo = failedResult.map { info ->
            info.pk = syncInfoPk
            info.sk = "${FAILED_INDEX}#${info.uid}"
            return@map info
        }

        // Batch write list of messages to new mailbox partition
        this.dynamoDBMapper.batchSave(ListUtils.union(newSuccessSyncInfo, newFailedSyncInfo))
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
    private fun storeMetaData(
        mailAccount: MailAccount,
        messageId: String,
        mailMetaData: MailMessageMetaData
    ): String {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val tenantKeyPrefix = "${mailAccount.tenant}/${mailAccount.username}/${mailAccount.email}/${messageId}"
        val mailObjectKey = "${tenantKeyPrefix}/metadata"
        this.s3Provider.putObjectString(mailBucket, mailObjectKey, mailMetaData.toJson())
        return URLEncoder.encode(mailObjectKey, "UTF-8")
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
        metadata.contentLength = attachment.size
        metadata.contentEncoding = attachment.encoding
        val putObjectRequest = PutObjectRequest(mailBucket, mailObjectKey, attachment.inputStream, metadata)
        // S3 Put input stream for up to 128K memory buffering by default, it is not enough for some case large attachment
        putObjectRequest.requestClientOptions.readLimit = 128 * 10 * 1024
        this.s3Provider.putObject(putObjectRequest)
        return URLEncoder.encode(mailObjectKey, "UTF-8")
    }


    /**
     * This method perform to save email body as html content to S3
     *
     * @param mailAccount: The mail account information.
     * @param messageId: The message Id.
     * @param htmlContent: The email body as html content
     *
     * @return The path of html email body which saved to the local storage
     * @exception
     */
    private fun storeHtmlContent(
        mailAccount: MailAccount, messageId: String, htmlContent: MailAttachment
    ): String {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val tenantKeyPrefix = "${mailAccount.tenant}/${mailAccount.username}/${mailAccount.email}/${messageId}"
        val htmlObjectKey = "${tenantKeyPrefix}/htmlContent/${htmlContent.name}"
        val metadata = ObjectMetadata()
        metadata.contentType = htmlContent.mimeType
        metadata.contentEncoding = htmlContent.encoding
        metadata.contentLength = htmlContent.size
        val putObjectRequest = PutObjectRequest(mailBucket, htmlObjectKey, htmlContent.inputStream, metadata)
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
        val mailTableName = this.secretManagerService.getAWSSecretValue("DYNAMO_MAIL_TABLE")
        val key: Map<String, AttributeValue> = hashMapOf(
            "pk" to AttributeValue().withS(mailBox.pk),
            "sk" to AttributeValue().withS(mailBox.sk)
        )
        val attributeUpdates: Map<String, AttributeValueUpdate> = hashMapOf(
            "syncStatus" to AttributeValueUpdate(AttributeValue().withS(status.toString()), AttributeAction.PUT)
        )
        val updateRequest: UpdateItemRequest = UpdateItemRequest()
            .withTableName(mailTableName)
            .withKey(key)
            .withAttributeUpdates(attributeUpdates)
        this.dynamoDBClient.updateItem(updateRequest)
    }

    /**
     * This method perform to update the mailbox size
     *
     * @param mailBoxPk: The mailbox pk
     * @param mailBoxSk: The mailbox sk
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
    fun updateMailBoxMetric(mailBoxPk: String, mailBoxSk: String, size: Long = 0, count: Int = 0, unread: Int = 0) {
        val mailTableName = this.secretManagerService.getAWSSecretValue("DYNAMO_MAIL_TABLE")
        val key: Map<String, AttributeValue> = hashMapOf(
            "pk" to AttributeValue().withS(mailBoxPk),
            "sk" to AttributeValue().withS(mailBoxSk)
        )
        val attributeUpdates: Map<String, AttributeValueUpdate> = hashMapOf(
            "size" to AttributeValueUpdate(AttributeValue().withN(size.toString()), AttributeAction.ADD),
            "count" to AttributeValueUpdate(AttributeValue().withN(count.toString()), AttributeAction.ADD),
            "unread" to AttributeValueUpdate(AttributeValue().withN(unread.toString()), AttributeAction.ADD)
        )
        val updateRequest: UpdateItemRequest = UpdateItemRequest()
            .withTableName(mailTableName)
            .withKey(key)
            .withAttributeUpdates(attributeUpdates)
        this.dynamoDBClient.updateItem(updateRequest)
    }

    /**
     * This method perform to notification new email received
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox information.
     * @param mailMessages: The mail message information to be notified.
     *
     * @return
     * @exception
     */
    fun notifyNewEmailsReceived(
        mailAccount: MailAccount,
        mailBox: MailBox,
        firstSync: Boolean,
        messageCount: Int,
        mailMessages: List<MailMessage>,
    ) {
        val notificationType = MAIL_NEW_EMAIL_RECEIVED
        val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
        val data: Any = if (firstSync) {
            mapOf(
                "firstSync" to true,
                "messageCount" to messageCount,
            )
        } else {
            mailMessages.map { it.toJson(mailCDN) }
        }

        this.appSyncProvider.executeSendNotification(
            notificationType, Gson().toJson(data), Date().time, mailAccount.tenant, mailAccount.username
        )
    }

    /**
     * Link [mailAccount] of [authenticatedUser] to our system and encryption password with [encryptionKey]
     * Throw [Exception] when failed to link email
     */
    fun linkEmail(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser, encryptionKey: String) = try {
        val userPk = "$TENANT_INDEX#${authenticatedUser.tenant}#$USER_INDEX#${authenticatedUser.username}"
        // Generate pk, sk for dynamo db
        mailAccount.pk = userPk
        mailAccount.sk = "$MAIL_ACCOUNT_INDEX#${mailAccount.email}"
        // Save default mailbox to dynamodb
        for ((index, name) in Constant.DEFAULT_MAILBOX.withIndex()) {
            val mailBox = MailBox()
            mailBox.pk = "$userPk#$MAIL_ACCOUNT_INDEX#${mailAccount.email}#$MAILBOX_INDEX"
            mailBox.sk = "$MAILBOX_INDEX#$name"
            mailBox.count = 0
            mailBox.unread = 0
            mailBox.order = index
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

    fun sendEmail(mailAccount: MailAccount, mailMessage: MailMessage) {
        // Send email
        this.smtpProvider.sendMail(mailAccount, mailMessage)
    }

    /**
     * Download [objectKey] from s3 bucket
     */
    fun getObjectFromS3(objectKey: String): S3ObjectInputStream {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val getObjectRequest = GetObjectRequest(mailBucket, objectKey)
        val fileObject = this.s3Provider.getObject(getObjectRequest)
        val inputStream = fileObject?.objectContent
        if (inputStream != null) {
            return inputStream
        } else {
            throw S3DownloadFileException("Can not download attachment [${objectKey}]")
        }
    }

    /**
     * Store sent email [mailMessage] of [tenant], [username] to s3 bucket
     * Return total size of email
     */
    fun storeSentEmailToS3(tenant: String, username: String, mailMessage: MailMessage) {
        val email = mailMessage.fromAsInternetAddress.address
        val messageId = mailMessage.messageId
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)

        // Move html content
        val tenantKeyPrefix = "${tenant}/${username}/${email}/${messageId}"
        val htmlObjectKey = "${tenantKeyPrefix}/htmlContent/index.html"
        this.s3Provider.moveObject(mailBucket, mailMessage.htmlContent, htmlObjectKey, "text/html", DEFAULT_ENCODING)
        mailMessage.htmlContent = htmlObjectKey.encodeToURL()
        mailMessage.size = this.s3Provider.getObjectSize(mailBucket, htmlObjectKey)

        // Move attachments
        mailMessage.attachments?.forEach {
            val attachmentObjectKey = "${tenantKeyPrefix}/${it.name}"
            this.s3Provider.moveObject(mailBucket, it.path, attachmentObjectKey, it.mimeType, it.encoding)
            it.path = attachmentObjectKey.encodeToURL()
            mailMessage.size += this.s3Provider.getObjectSize(mailBucket, attachmentObjectKey)
        }
        logger.info("Store sent email to s3 successfully")
    }

    /**
     * Generate presigned url to put [fileName] with [contentType] to s3 bucket
     * Return [PresignedUrlResponsePayload] object
     */
    fun generatePresignedUrl(fileName: String, contentType: String): PresignedUrlResponsePayload {
        val localTime = LocalDateTime.now().plusMinutes(DEFAULT_PRESIGNED_URL_EXPIRATION.toLong())
        val expiration = Date.from(localTime.atZone(ZoneId.systemDefault()).toInstant())
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val objectKey = "${S3_TEMPORARY_UPLOAD_FOLDER}/${UUID.randomUUID()}/${fileName}"
        val generatePresignedUrlRequest =
            GeneratePresignedUrlRequest(mailBucket, objectKey, HttpMethod.PUT).withContentType(contentType)
                .withExpiration(expiration)
        val url = this.s3Provider.generatePresignedUrl(generatePresignedUrlRequest)
        return PresignedUrlResponsePayload(url.toString(), objectKey)
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
        val uppercaseMailBox = mailBox.uppercase()
        val pk =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#${uppercaseMailBox}"
        val sk = "$MAILBOX_INDEX#${uppercaseMailBox}"
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

    /**
     * Change mailbox [from] to [to] for mail [message] of email [mail] that belongs to [tenant] [username]
     */
    fun changeMailboxOfMessage(
        tenant: String,
        username: String,
        from: String,
        to: String,
        mail: String,
        message: String
    ) {
        // Query mail message
        val pkWithoutMailbox =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#"
        val pk = "$pkWithoutMailbox$from"
        val sk = "${MESSAGE_INDEX}#$message"
        val mailMessage = this.dynamoDBMapper.load(MailMessage::class.java, pk, sk)
        val newMailMessage = mailMessage.clone()

        // Delete old mail message
        this.dynamoDBMapper.delete(mailMessage)

        // Insert the new one
        newMailMessage.pk = "$pkWithoutMailbox$to"
        newMailMessage.lsi1 = newMailMessage.lsi1?.replace(from.uppercase(), to.uppercase())
        this.dynamoDBMapper.save(newMailMessage)
    }

    /**
     * Change mailbox [from] to [to] for mail [messages] of email [mail] that belongs to [tenant] [username]
     * Do following steps:
     * - Batch load message items
     * - Batch delete message items from old partition belongs to old mailbox [from]
     * - Batch write message items to new partition belongs to new mailbox [to]
     */
    fun changeMailboxOfMessages(
        tenant: String,
        username: String,
        from: String,
        to: String,
        mail: String,
        messages: List<String>
    ) {
        // Query mail messages
        val pkWithoutMailbox =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#"
        val pk = "$pkWithoutMailbox$from"

        // Use batch load to get MailMessage items
        val keysList = messages.map {
            val keyPair = KeyPair()
            keyPair.hashKey = pk
            keyPair.rangeKey = "${MESSAGE_INDEX}#$it"
            return@map keyPair
        }
        val mailMessagesKeyPairTable = mutableMapOf<Class<*>, List<KeyPair>>(
            MailMessage::class.java to keysList
        )
        val batchLoadMessagesResults = this.dynamoDBMapper.batchLoad(mailMessagesKeyPairTable)
        val messagesToUpdate = batchLoadMessagesResults.values.first().toList()
        val newMailMessages = messagesToUpdate.map {
            val mailMessageObj = (it as MailMessage).clone()
            mailMessageObj.pk = "$pkWithoutMailbox$to"
            mailMessageObj.lsi1 = mailMessageObj.lsi1?.replace(from.uppercase(), to.uppercase())
            return@map mailMessageObj
        }

        // Batch delete list of messages related to old partition key
        this.dynamoDBMapper.batchDelete(messagesToUpdate)

        // Batch write list of messages to new mailbox partition
        this.dynamoDBMapper.batchSave(newMailMessages)
    }
}
