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
import com.amazonaws.services.kms.model.NotFoundException
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.google.gson.Gson
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.component.VEvent
import org.apache.commons.collections.ListUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.time.DateUtils
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import tech.blueglacier.email.Attachment
import traveloffice.mail.common.Constant.Companion.CODE_VERIFIER_INDEX
import traveloffice.mail.common.Constant.Companion.DEFAULT_ENCODING
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX
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
import traveloffice.mail.entities.apis.AutoLinkEmailRequest
import traveloffice.mail.entities.oauth2.CodeVerifier
import traveloffice.mail.entities.oauth2.OAuth2User
import traveloffice.mail.entities.search.MailMessageSearch
import traveloffice.mail.entities.search.MailMessageSearchRequest
import traveloffice.mail.exceptions.MailAccountWasLinked
import traveloffice.mail.exceptions.S3DownloadFileException
import traveloffice.mail.exceptions.UnSupportedInboxType
import traveloffice.mail.extensions.decrypt
import traveloffice.mail.extensions.encodeToURL
import traveloffice.mail.extensions.encrypt
import traveloffice.mail.extensions.toSearchIndex
import traveloffice.mail.providers.*
import traveloffice.mail.providers.oauth2.OAuth2ProviderFactory
import traveloffice.mail.services.SecretManagerService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
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

    @Autowired
    lateinit var searchProvider: SearchProvider

    @Autowired
    lateinit var oAuth2ProviderFactory: OAuth2ProviderFactory

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
        mailMessage.messageId = mailProvider.getMessageId(message) ?: UUID.randomUUID().toString()
        mailMessage.plainTextEmailBody = email.plainTextEmailBody?.`is`
        if (email.htmlEmailBody != null)
            mailMessage.rawHtmlContent = getHtmlContentFromEmail(email.htmlEmailBody)
        else if (email.plainTextEmailBody != null) {
            // Convert plain text to html content
            val stringContent = String(email.plainTextEmailBody.`is`.readAllBytes())
            val stringContentAsHtml = stringContent.replace(Regex("(\r\n|\n)"), "<br>")
            email.plainTextEmailBody.`is` = stringContentAsHtml.byteInputStream()
            mailMessage.rawHtmlContent = getHtmlContentFromEmail(email.plainTextEmailBody)
        }
        mailMessage.rawCalendarEvent = getCalendarEventFromEmail(email.calendarBody)
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
        mailMessage.rawHtmlContent = MailAttachment.Builder()
            .withName("index.html")
            .withMimeType("text/html; charset=utf-8")
            .withInputStream(TESTING_MAIL_CONTEXT.byteInputStream())
            .build()
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

    private fun getCalendarEventFromEmail(calendarBody: Attachment?): MailAttachment? {
        if (calendarBody == null) return null
        return MailAttachment.Builder()
            .withName(calendarBody.attachmentName)
            .withMimeType(calendarBody.bd.mimeType)
            .withEncoding(calendarBody.bd.charset)
            .withSize(calendarBody.attachmentSize.toLong())
            .withInputStream(calendarBody.`is`)
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
    fun getMailAccount(tenant: String, username: String, email: String): MailAccount {
        val mailAccount = this.dynamoDBMapper.load(
            MailAccount::class.java,
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}",
            "${MAIL_ACCOUNT_INDEX}#${email}"
        ) ?: throw NotFoundException("Mail account [$email] not found!, tenant [$tenant] username [$username]")
        //Decrypt password
        val encryptionKey = this.secretManagerService.getAWSSecretValue(ENCRYPT_SECRET_KEY)
        mailAccount.inboundPassword = mailAccount.inboundPassword?.decrypt(encryptionKey)
        mailAccount.outboundPassword = mailAccount.outboundPassword?.decrypt(encryptionKey)
        val oauth2Token = mailAccount.oauth2Token
        if (oauth2Token != null) {
            // Check the exp date
            val expDate = oauth2Token.expires_in
            val now = Date().time.div(1000L)
            if (expDate >= now) {
                // refresh token
                val oAuth2Provider = oAuth2ProviderFactory.makeProvider(mailAccount.provider)
                val newToken = oAuth2Provider.refreshEmailToken(oauth2Token.refresh_token!!)
                //Refactor the exp time of oauth token, convert to epoch time
                val expTimeInSecond = newToken!!.expires_in
                newToken.expires_in = DateUtils.addSeconds(Date(), expTimeInSecond.toInt()).time.div(1000L)
                newToken.refresh_token = oauth2Token.refresh_token
                mailAccount.oauth2Token = newToken
                dynamoDBMapper.save(mailAccount)
            }
        }
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
     * Get total mail storage for given [username] that belong to [tenant]
     *
     * Returns total storage
     */
    fun getUserMailStorage(tenant: String, username: String): Long {
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val mailAccountBucketKey = "$tenant/$username"
        return s3Provider.getObjectsSize(mailBucket, mailAccountBucketKey)
    }

    /**
     * Get OAuth login url for given [provider] [username] that belong to [tenant]
     *
     * Returns OAuth url
     */
    fun getOAuth2Url(tenant: String, username: String, provider: String, mail: String): String {
        val oAuth2Provider = oAuth2ProviderFactory.makeProvider(provider)
        val codeChallenge = oAuth2Provider.generateCodeChallenge(provider)
        codeChallenge.pk = "$TENANT_INDEX#$tenant#$USER_INDEX#$username$CODE_VERIFIER_INDEX"
        // Store code challenge to dynamodb
        dynamoDBMapper.save(codeChallenge)
        // Generate OAuth login url
        return oAuth2Provider.generateOAuth2LoginUrl(codeChallenge.codeChallenge, codeChallenge.state, mail).toString()
    }

    /**
     * Get OAuth user information for given [autoLinkEmailRequest] that belong to [tenant] [username]
     *
     * Returns OAuth url
     */
    fun getOAuthUserInfo(tenant: String, username: String, autoLinkEmailRequest: AutoLinkEmailRequest): OAuth2User {
        val state = autoLinkEmailRequest.state!!
        val code = autoLinkEmailRequest.code!!

        val codeVerifier = dynamoDBMapper.load(
            CodeVerifier::class.java,
            "$TENANT_INDEX#$tenant#$USER_INDEX#$username$CODE_VERIFIER_INDEX",
            state
        ) ?: throw Exception("Code Verifier has expired or does not exist tenant [$tenant] username [$username]")

        val oAuth2Provider = oAuth2ProviderFactory.makeProvider(codeVerifier.provider)
        val oAuth2User = oAuth2Provider.getUserInfo(codeVerifier.code, code)

        //Refactor the exp time of oauth token, convert to epoch time
        val expTimeInSecond = oAuth2User.oAuth2Token!!.expires_in
        oAuth2User.oAuth2Token!!.expires_in = DateUtils.addSeconds(Date(), expTimeInSecond.toInt()).time.div(1000L)

        // Remove code verifier after first time get OAuth token
        dynamoDBMapper.delete(codeVerifier)
        return oAuth2User
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
        mailMessage.lsi1 = "$MAILBOX_INDEX#${mailBoxName}#${mailMessage.sentDate ?: mailMessage.updatedAt}"
        mailMessage.mailbox = mailBoxName

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
        val calendarEmailEvent = mailMessage.rawCalendarEvent
        if (calendarEmailEvent != null) {
            // Keep the calendar input stream to get brief content
            val byteArrayOutputStream = ByteArrayOutputStream()
            calendarEmailEvent.inputStream?.transferTo(byteArrayOutputStream)
            calendarEmailEvent.inputStream = ByteArrayInputStream(byteArrayOutputStream.toByteArray())
            mailMessage.calendarEvent = storeAttachment(mailAccount, mailMessage.messageId, calendarEmailEvent)
            calendarEmailEvent.inputStream = ByteArrayInputStream(byteArrayOutputStream.toByteArray())
        }
        // Get the brief content of the message
        val defaultBriefContentSize = this.secretManagerService.getEnv("MAX_MESSAGE_BRIEF_CONTENT_SIZE").toInt()
        val stringContent = getStringContentFromEmail(mailMessage)
        mailMessage.plainTextContent = stringContent
        mailMessage.briefContent = stringContent
            .substring(0, defaultBriefContentSize.coerceAtMost(stringContent.length))
            .replace("\n", "")
            .replace("\r", "")

        // Save the metadata
        val metaData = MailMessageMetaData(
            mailMessage.to.rawAddress,
            mailMessage.cc?.rawAddress,
            mailMessage.bcc?.rawAddress
        )
        mailMessage.metaData = storeMetaData(mailAccount, mailMessage.messageId, metaData)

        // Save to database
        this.dynamoDBMapper.save(mailMessage)

        // Start save to search database
        val mailMessageSearch = mailMessage.toSearchEntity()
        searchProvider.addDocument(mailAccount.searchIndex, mailMessageSearch)
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
     * @param mailMetaData: The attachment information
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
            "sk" to AttributeValue().withS(mailBox.sk),
        )
        val attributeUpdates: Map<String, AttributeValueUpdate> = hashMapOf(
            "syncStatus" to AttributeValueUpdate(AttributeValue().withS(status.toString()), AttributeAction.PUT),
            "updatedAt" to AttributeValueUpdate(AttributeValue().withN(Date().time.toString()), AttributeAction.PUT),
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
        val (tenant, username) = authenticatedUser
        val userPk = "$TENANT_INDEX#$tenant#$USER_INDEX#$username"
        // Generate pk, sk for dynamo db
        mailAccount.pk = userPk
        mailAccount.sk = "$MAIL_ACCOUNT_INDEX#${mailAccount.email}"
        // Encrypt mail password
        mailAccount.inboundPassword = mailAccount.inboundPassword?.encrypt(encryptionKey)
        mailAccount.outboundPassword = mailAccount.outboundPassword?.encrypt(encryptionKey)
        // Set tenant, username for mail account
        mailAccount.tenant = tenant
        mailAccount.username = username
        //Create mail message search index
        val messageIndex = "${userPk}#$MAIL_ACCOUNT_INDEX#${mailAccount.email}#${MESSAGE_INDEX}".toSearchIndex()
        mailAccount.searchIndex = messageIndex
        // Save mail account information to dynamodb
        this.dynamoDBMapper.save(mailAccount)
        // Create the search engine index
        this.searchProvider.createIndex(messageIndex, MailMessageSearch.IndexConfig().defaultSetting(), "id")

        val mailboxes = getMailBoxes(tenant, username, mailAccount.email)
        if (mailboxes.isNotEmpty() && mailboxes.first().ttl != null) {
            // Restore from old mail data
            logger.info("Updating mail account [${mailAccount.email}] ttl")
            updateMailAccountTTL(tenant, username, mailAccount)
            logger.info("Updated mail account [${mailAccount.email}] ttl successfully")
            // Restore all mail storage from temporary folder
            val mailBucket = secretManagerService.getAWSSecretValue(MAIL_BUCKET)
            val targetBucket = "$tenant/$username/${mailAccount.email}"
            val orgBucket = "temporary-delete/$targetBucket"
            logger.info("Moving mail storage of mail account [${mailAccount.email}] to temporary delete")
            s3Provider.moveObjects(mailBucket, orgBucket, targetBucket)
            logger.info("Move mail storage of mail account [${mailAccount.email}] to temporary delete successfully")
        } else {
            // create new default mailbox to dynamodb
            for ((index, name) in DEFAULT_MAILBOX.withIndex()) {
                val mailBox = MailBox()
                mailBox.pk = "$userPk#$MAIL_ACCOUNT_INDEX#${mailAccount.email}#$MAILBOX_INDEX"
                mailBox.sk = "$MAILBOX_INDEX#$name"
                mailBox.order = index
                this.dynamoDBMapper.save(mailBox)
            }
        }

        logger.info("Save mail account ${mailAccount.email} to dynamodb successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw Exception("Failed to link mail account ${mailAccount.email}")
    }

    /**
     * Unlink [mailAccount] of [tenant] and [username] to our system
     * Throw [Exception] when failed to link email
     */
    fun unlinkEmail(tenant: String, username: String, mailAccount: MailAccount) = try {
        // Set expiration date for all record belong to mail account, accuracy by hour
        val expDate = DateUtils.ceiling(DateUtils.addDays(Date(), 7), Calendar.HOUR)
        logger.info("Updating mail account [${mailAccount.email}] ttl")
        updateMailAccountTTL(tenant, username, mailAccount, expDate)
        logger.info("Updated mail account [${mailAccount.email}] ttl successfully")
        // Move all mail storage to temporary folder that will be expiration after 7 days
        val mailBucket = secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val orgBucket = "$tenant/$username/${mailAccount.email}"
        val targetBucket = "temporary-delete/${orgBucket}"
        logger.info("Moving mail storage of mail account [${mailAccount.email}] to temporary delete")
        s3Provider.moveObjects(mailBucket, orgBucket, targetBucket)
        logger.info("Moved mail storage of mail account [${mailAccount.email}] to temporary delete successfully")
        // Remove mail account from database
        dynamoDBMapper.delete(mailAccount)
    } catch (e: Exception) {
        logger.error(e.toString())
        throw Exception("Failed to unlink mail account ${mailAccount.email} from tenant $tenant and username $username")
    }

    private fun updateMailAccountTTL(
        tenant: String,
        username: String,
        mailAccount: MailAccount,
        expDate: Date? = null
    ) {
        val mail = mailAccount.email
        // For DynamoDB TTL
        val dynamoDBTTL = expDate?.time?.div(1000L)
        // For search TTL
        val searchTTL = dynamoDBTTL ?: 0
        val mailBoxes = getMailBoxes(tenant, username, mail)
        val mailBoxesWithTTL = mailBoxes.map {
            it.ttl = dynamoDBTTL
            return@map it
        }
        dynamoDBMapper.batchSave(mailBoxesWithTTL)

        mailBoxes.forEach { mailbox ->
            // Add ttl to all mail message attribute
            var lastEvaluatedKey: String? = null
            do {
                val (token, messages) = getMailsInsideMailBox(tenant, username, mail, mailbox.name, lastEvaluatedKey)
                lastEvaluatedKey = (if (token != "null") token else null)
                val mailMessagesTTL = messages.map { message ->
                    message.ttl = dynamoDBTTL
                    return@map message
                }
                val searchUpdateMessages = messages.map { message ->
                    mapOf(
                        "id" to message.messageId.toSearchIndex(),
                        "ttl" to searchTTL
                    )
                }
                searchProvider.updateDocuments(mailAccount.searchIndex, searchUpdateMessages)
                dynamoDBMapper.batchSave(mailMessagesTTL)
            } while (lastEvaluatedKey != null)
        }
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
    fun storeEmailToS3(tenant: String, username: String, mailMessage: MailMessage) {
        val email = mailMessage.fromAsInternetAddress.address
        val messageId = mailMessage.messageId
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)

        // Move html content
        val tenantKeyPrefix = "${tenant}/${username}/${email}/${messageId}"
        val htmlObjectKey = "${tenantKeyPrefix}/htmlContent/index.html"
        if (mailMessage.htmlContent.contains(S3_TEMPORARY_UPLOAD_FOLDER)) {
            this.s3Provider.moveObject(
                mailBucket,
                mailMessage.htmlContent,
                htmlObjectKey,
                "text/html",
                DEFAULT_ENCODING
            )
        } else {
            this.s3Provider.copyObject(
                mailBucket,
                mailMessage.htmlContent,
                htmlObjectKey,
                "text/html",
                DEFAULT_ENCODING
            )
        }
        mailMessage.htmlContent = htmlObjectKey.encodeToURL()
        mailMessage.size = this.s3Provider.getObjectSize(mailBucket, htmlObjectKey)

        // Move attachments
        mailMessage.attachments?.forEach {
            val attachmentObjectKey = "${tenantKeyPrefix}/${it.name}"
            if (it.path.contains(S3_TEMPORARY_UPLOAD_FOLDER)) {
                this.s3Provider.moveObject(mailBucket, it.path, attachmentObjectKey, it.mimeType, it.encoding)
            } else {
                this.s3Provider.copyObject(mailBucket, it.path, attachmentObjectKey, it.mimeType, it.encoding)
            }
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
        lastEvaluatedKeyString: String? = null,
        limit: Int = DEFAULT_NUMBER_OF_MAILS_TO_FETCH,
        sortMode: String = "DESC",
        date: Long? = null
    ): Pair<String, MutableList<MailMessage>> {
        // Pk, sk
        val uppercaseMailBox = mailBox.uppercase()
        val pk =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#${uppercaseMailBox}"
        val sk = if (date == null) {
            "$MAILBOX_INDEX#${uppercaseMailBox}"
        } else {
            "$MAILBOX_INDEX#${uppercaseMailBox}#${date}"
        }
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
        val keyConditionExpression = if (date == null) {
            "pk = :pk and begins_with(lsi1, :lsi1)"
        } else {
            "pk = :pk and lsi1 > :lsi1"
        }
        val queryExpression = DynamoDBQueryExpression<MailMessage>()
            .withIndexName("Index1") // Index for sorting
            .withKeyConditionExpression(keyConditionExpression)
            .withScanIndexForward(sortMode == "ASC") // DESC sk
            .withExclusiveStartKey(lastEvaluatedKey)
            .withLimit(limit) // Limit number of mails returned
            .withExpressionAttributeValues(eav)

        val result = this.dynamoDBMapper.queryPage(MailMessage::class.java, queryExpression)
        return Pair(Gson().toJson(ItemUtils.toSimpleMapValue<Any>(result.lastEvaluatedKey)), result.results)
    }

    /**
     * Change mailbox [from] to [to] for mail [message] of email [mail] that belongs to [tenant] [username]
     * In case sending draft email, need to update lsi1 follow [sentDate]
     */
    fun changeMailboxOfMessage(
        tenant: String,
        username: String,
        from: String,
        to: String,
        mail: String,
        message: String,
        sentDate: Long? = null,
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
        newMailMessage.sk = sk
        newMailMessage.updatedAt = Date().time
        if (sentDate === null) {
            newMailMessage.lsi1 = mailMessage.lsi1?.replace(from.uppercase(), to.uppercase())
        } else {
            newMailMessage.lsi1 = "$MAILBOX_INDEX#${to.uppercase()}#${sentDate}"
            newMailMessage.sentDate = sentDate
        }
        this.dynamoDBMapper.save(newMailMessage)

        // Update search engine items
        val mailAccount = this.getMailAccount(tenant, username, mail)
        val searchUpdateMessages = listOf(
            mapOf(
                "id" to newMailMessage.messageId.toSearchIndex(),
                "box" to to
            )
        )
        this.searchProvider.updateDocuments(mailAccount.searchIndex, searchUpdateMessages)
    }

    /**
     * Change mailbox [from] to [to] for mail [messages] of email [mail] that belongs to [tenant] [username]
     * Do following steps:
     * - Batch load message items
     * - Batch delete message items from old partition belongs to old mailbox [from]
     * - Batch write message items to new partition belongs to new mailbox [to]
     * - Batch update the mailbox of message items from search engine
     */
    fun changeMailboxOfMessages(
        tenant: String,
        username: String,
        from: String,
        to: String,
        mail: String,
        messages: List<String>
    ) {
        // Verify account exists
        val mailAccount = this.getMailAccount(tenant, username, mail)

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
            mailMessageObj.sk = it.sk
            mailMessageObj.lsi1 = it.lsi1?.replace(from.uppercase(), to.uppercase())
            return@map mailMessageObj
        }

        // Batch delete list of messages related to old partition key
        this.dynamoDBMapper.batchDelete(messagesToUpdate)

        // Batch write list of messages to new mailbox partition
        this.dynamoDBMapper.batchSave(newMailMessages)

        // Batch update search engine items
        val searchUpdateMessages = messagesToUpdate.map {
            return@map mapOf(
                "id" to (it as MailMessage).messageId.toSearchIndex(),
                "box" to to
            )
        }
        this.searchProvider.updateDocuments(mailAccount.searchIndex, searchUpdateMessages)
    }

    /**
     * Replicate email from [mailAccount] to search engine to prevent missing search items
     * Do following steps:
     * - Batch load message items
     * - Batch write message items to search engine
     */
    fun replicateEmailToSearchEngine(mailAccount: MailAccount) {
        val tenant = mailAccount.tenant
        val username = mailAccount.username
        val email = mailAccount.email

        logger.info(
            "Replicating email from database to search engine " +
                    "from Tenant [$tenant] User [$username] " +
                    "Mail Account [$email] "
        )

        val mailBoxes = this.getMailBoxes(tenant, username, email)
        mailBoxes.forEach {
            val (_, lastMessages) = this.getMailsInsideMailBox(tenant, username, email, it.name, null, 1)
            // If first sync, do nothing
            if (lastMessages.isEmpty())
                return@forEach
            // Get the last sync item from search engine
            val searchResult =
                this.searchProvider.search(
                    mailAccount.searchIndex,
                    MailMessageSearchRequest("", 0, 1, listOf("date"), listOf("date:desc"), listOf("box = ${it.name}"))
                )
            // Not found any message in search engine, going to replicate all message to search engine
            var lastEvaluatedKey: String? = null
            do {
                val (token, messages) = if (searchResult == null || searchResult.hits.isNullOrEmpty())
                    this.getMailsInsideMailBox(tenant, username, email, it.name, lastEvaluatedKey, sortMode = "ASC")
                else {
                    val searchEngineLastSyncTime = (searchResult.hits.first()["date"] as Double).toLong()
                    val lastSyncTime = lastMessages.first().sentDate
                    if (searchEngineLastSyncTime >= lastSyncTime!!)
                        return@forEach
                    this.getMailsInsideMailBox(
                        tenant,
                        username,
                        email,
                        it.name,
                        lastEvaluatedKey,
                        sortMode = "ASC",
                        date = searchEngineLastSyncTime
                    )
                }
                lastEvaluatedKey = (if (token != "null") token else null)
                val mailMessagesSearch = messages.map { message ->
                    message.mailbox = it.name
                    return@map toMailMessageSearch(message).toJsonString()
                }
                this.searchProvider.addDocuments(mailAccount.searchIndex, mailMessagesSearch)
                logger.info(
                    "Mailbox [${it.name}] Replicated ${mailMessagesSearch.size} email from database to search engine " +
                            "from Tenant [$tenant] User [$username] " +
                            "Mail Account [$email] "
                )
            } while (lastEvaluatedKey != null)
        }
        logger.info(
            "Replicated email from database to search engine " +
                    "from Tenant [$tenant] User [$username] " +
                    "Mail Account [$email] "
        )
    }

    /**
     * Batch delete mails of email [messagesId] that belongs to [tenant] [username]
     * Do following steps:
     * - Batch load message items
     * - Batch delete the s3 content
     * - Batch delete item from database
     * - Batch delete item from search engine
     */
    fun deleteMailMessages(
        tenant: String,
        username: String,
        mail: String,
        mailbox: String,
        messagesId: List<String>
    ) {
        // Verify account exists
        val mailAccount = this.getMailAccount(tenant, username, mail)

        // Query mail messages
        val pk =
            "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${MAIL_ACCOUNT_INDEX}#${mail}#${MAILBOX_INDEX}#$mailbox"

        // Use batch load to delete MailMessage items
        val keysList = messagesId.map {
            return@map KeyPair().withHashKey(pk).withRangeKey("${MESSAGE_INDEX}#$it")
        }
        val mailMessagesKeyPairTable = mutableMapOf<Class<*>, List<KeyPair>>(
            MailMessage::class.java to keysList
        )

        val batchLoadMessagesResults = this.dynamoDBMapper.batchLoad(mailMessagesKeyPairTable)
        val messagesToDelete = batchLoadMessagesResults.values.first().toList()

        // Remove all mail message storage
        val storageKeys = messagesToDelete.map {
            return@map KeyVersion("${tenant}/${username}/${mail}/${(it as MailMessage).messageId}")
        }
        val mailBucket = this.secretManagerService.getAWSSecretValue(MAIL_BUCKET)
        val multiObjectDeleteRequest: DeleteObjectsRequest = DeleteObjectsRequest(mailBucket)
            .withKeys(storageKeys)
            .withQuiet(false)
        this.s3Provider.deleteObjects(multiObjectDeleteRequest)

        // Delete message from search engine
        val searchMessagesIdToDelete = messagesToDelete.map { (it as MailMessage).messageId.toSearchIndex() }
        this.searchProvider.deleteDocuments(mailAccount.searchIndex, searchMessagesIdToDelete)

        // Batch delete list of messages
        this.dynamoDBMapper.batchDelete(messagesToDelete)
    }

    private fun getStringContentFromEmail(mailMessage: MailMessage): String {
        // Get the brief content of the message
        var stringContent = ""
        try {
            val rawHtmlContentIs = mailMessage.rawHtmlContent?.inputStream
            val calendarEmailEventIs = mailMessage.rawCalendarEvent?.inputStream
            if (rawHtmlContentIs != null) {
                // Parse brief content from html content
                val doc = Jsoup.parse(rawHtmlContentIs, StandardCharsets.UTF_8.toString(), "")
                stringContent = IOUtils.toString(doc.text().byteInputStream(), StandardCharsets.UTF_8).trim()
            } else if (calendarEmailEventIs != null) {
                // Parse brief content from calendar content
                val builder = CalendarBuilder()
                val calendar = builder.build(calendarEmailEventIs)
                stringContent = (calendar.components.first() as VEvent).description.value
            }

        } catch (e: IOException) {
            logger.error("Cannot get brief content of message [${mailMessage.messageId}]")
        } catch (e: ParserException) {
            logger.error("Cannot get brief content of message [${mailMessage.messageId}] wrong calendar event")
        }
        return stringContent
    }

    private fun toMailMessageSearch(mailMessage: MailMessage): MailMessageSearch {
        if (mailMessage.htmlContent != "") {
            mailMessage.rawHtmlContent = MailAttachment.Builder()
                .withInputStream(getObjectFromS3(URLDecoder.decode(mailMessage.htmlContent, Charset.defaultCharset())))
                .build()
        }
        if (mailMessage.calendarEvent != "") {
            mailMessage.rawCalendarEvent = MailAttachment.Builder()
                .withInputStream(
                    getObjectFromS3(
                        URLDecoder.decode(
                            mailMessage.calendarEvent,
                            Charset.defaultCharset()
                        )
                    )
                )
                .build()
        }

        if (mailMessage.metaData != "") {
            val rawMetaData = getObjectFromS3(URLDecoder.decode(mailMessage.metaData, Charset.defaultCharset()))
            val metaData = Gson().fromJson(String(rawMetaData.readAllBytes()), MailMessageMetaData::class.java)
            mailMessage.to.rawAddress = metaData.to
            if (mailMessage.cc != null && metaData.cc != null)
                mailMessage.cc!!.rawAddress = metaData.cc!!
            if (mailMessage.bcc != null && metaData.bcc != null)
                mailMessage.bcc!!.rawAddress = metaData.bcc!!
        }
        mailMessage.plainTextContent = getStringContentFromEmail(mailMessage)

        return mailMessage.toSearchEntity()
    }
}
