package traveloffice.mail.repositories

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
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
import traveloffice.mail.common.Constant.Companion.ENCRYPT_SECRET_KEY
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_BUCKET
import traveloffice.mail.common.Constant.Companion.MAIL_CDN
import traveloffice.mail.common.Constant.Companion.MAIL_NEW_EMAIL_RECEIVED
import traveloffice.mail.common.Constant.Companion.MESSAGE_INDEX
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.common.Constant.Companion.USER_INDEX
import traveloffice.mail.entities.*
import traveloffice.mail.exceptions.UnSupportedInboxType
import traveloffice.mail.extensions.decrypt
import traveloffice.mail.providers.AppSyncProvider
import traveloffice.mail.providers.MailProviderFactory
import traveloffice.mail.providers.S3Provider
import traveloffice.mail.services.SecretManagerService
import java.net.URLEncoder
import java.util.*
import javax.mail.Message
import javax.mail.internet.InternetAddress

@Repository
@DependsOn(value = ["SecretManager"])
class MailRepository {
    @Autowired
    lateinit var mailProviderFactory: MailProviderFactory

    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

    @Autowired
    lateinit var appSyncProvider: AppSyncProvider

    @Autowired
    lateinit var s3Provider: S3Provider

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
        logger.info("getLastMailBoxSync MailBox[$mailBox] with MailAccount [$mailAccount]")
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
        val emailContentStream = email.plainTextEmailBody.`is`
        var briefContent = ""
        if (emailContentStream.available() > 0) {
            val defaultBriefContentSize = this.secretManagerService.getEnv("MAX_MESSAGE_BRIEF_CONTENT_SIZE").toInt()
            briefContent = if (defaultBriefContentSize < emailContentStream.available()) {
                String(emailContentStream.readNBytes(defaultBriefContentSize))
            } else {
                String(emailContentStream.readAllBytes())
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
        mailMessage.subject = email.emailSubject
        mailMessage.from = getAddressFromHeader(email.fromEmailHeaderValue)
        mailMessage.to = getAddressFromHeader(email.toEmailHeaderValue)
        mailMessage.cc = getAddressFromHeader(email.ccEmailHeaderValue)
        mailMessage.bcc = getAddressFromHeader(email.header.getField("Bcc")?.body)
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
    private fun getAddressFromHeader(headerValue: String?): List<EmailAddress>? {
        if (headerValue.isNullOrEmpty()) return null
        return InternetAddress.parse(headerValue).map {
            val emailAddress = EmailAddress()
            emailAddress.address = it.address
            emailAddress.personal = it.personal ?: null
            return@map emailAddress
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
            "${TENANT_INDEX}#${mailAccount.tenant}#" +
                    "${USER_INDEX}#${mailAccount.username}#" +
                    "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" +
                    MAILBOX_INDEX,
            "$MAILBOX_INDEX#${mailBox}"
        )
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
            "${TENANT_INDEX}#${mailAccount.tenant}#" +
                    "${USER_INDEX}#${mailAccount.username}#" +
                    "${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#" +
                    "${MAILBOX_INDEX}#${mailBox.name}"
        mailMessage.sk = "${MESSAGE_INDEX}#${mailMessage.messageId}"
        mailMessage.lsi1 = "$MAILBOX_INDEX#${mailBox.name}"
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
        val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
        return "$mailCDN/${URLEncoder.encode(mailObjectKey, "UTF-8")}"
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
        val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
        return "$mailCDN/${URLEncoder.encode(htmlObjectKey, "UTF-8")}"
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
    fun updateMailBoxLastSyncTime(mailBox: MailBox, syncTime: Long) {
        val box = MailBox()
        box.pk = mailBox.pk
        box.sk = mailBox.sk
        box.lastMailboxSync = syncTime
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
            .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
            .build()
        return this.dynamoDBMapper.save(mailBox, config)
    }

    /**
     * This method perform to notification new email received
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox information.
     * @param mailMessage: The mail message information to be notified.
     *
     * @return The path of html email body which saved to the local storage
     * @exception
     */
    fun notifyNewEmailReceived(mailAccount: MailAccount, mailBox: MailBox, mailMessage: MailMessage) {
        val notificationType = MAIL_NEW_EMAIL_RECEIVED
        val data = this.dynamoDBMapper.load(
            MailMessage::class.java,
            mailMessage.pk,
            mailMessage.sk
        )

        this.appSyncProvider.executeSendNotification(
            notificationType,
            Gson().toJson(data),
            Date().time,
            mailAccount.tenant,
            mailAccount.username
        )
    }
}
