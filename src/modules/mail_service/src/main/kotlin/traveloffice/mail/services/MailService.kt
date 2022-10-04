package traveloffice.mail.services

import com.amazonaws.services.kms.model.NotFoundException
import com.google.gson.GsonBuilder
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_DELETE
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_DRAFT
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_INBOX
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_SENT
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_SPAM
import traveloffice.mail.common.Constant.Companion.MAIL_CDN
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.common.Constant.Companion.USER_INDEX
import traveloffice.mail.common.SyncMessageLock
import traveloffice.mail.entities.*
import traveloffice.mail.entities.apis.ChangeMailboxRequestBody
import traveloffice.mail.exceptions.InvalidArguments
import traveloffice.mail.exceptions.MailAccountNotExistException
import traveloffice.mail.exceptions.MailBoxIsStillSyncing
import traveloffice.mail.exceptions.MailBoxNotExisted
import traveloffice.mail.extensions.subString
import traveloffice.mail.repositories.MailRepository
import java.util.*
import javax.mail.Flags

@Service
@DependsOn("DynamoDBMapper")
class MailService {
    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var mailRepository: MailRepository

    var logger: Logger = LoggerFactory.getLogger(MailRepository::class.java)

    /**
     * [authenticatedUser] user send email with [sendMailRequestPayload]
     * Return [MailMessage] object
     */
    fun sendEmail(authenticatedUser: AuthenticatedUser, sendMailRequestPayload: SendMailRequestPayload): MailMessage {
        val gson = GsonBuilder().create()
        val storeMessage = gson.fromJson(gson.toJson(sendMailRequestPayload), MailMessage::class.java)
        val sendingMessage = gson.fromJson(gson.toJson(sendMailRequestPayload), MailMessage::class.java)

        // Check mail account exist or not
        val emailAddress = sendingMessage.fromAsInternetAddress.address
        val (tenant, username) = authenticatedUser
        val mailAccount: MailAccount = this.mailRepository.getMailAccount(
            tenant,
            username,
            emailAddress
        ) ?: throw MailAccountNotExistException("Tenant [${tenant}], User [${username}], Mail account [$emailAddress] doesn't exist")

        // Download Html content from S3 Pre-signed url
        sendingMessage.htmlContentInputStream = this.mailRepository.getObjectFromS3(sendingMessage.htmlContent)
        // Download attachments from s3 bucket
        if (sendingMessage.attachments != null) {
            sendingMessage.attachments?.forEach {
                it.inputStream = this.mailRepository.getObjectFromS3(it.path)
            }
        }

        // Send email
        storeMessage.messageId = UUID.randomUUID().toString()
        this.mailRepository.sendEmail(mailAccount, sendingMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Send email successfully")

        // Parse brief content from html content
        val doc = Jsoup.parse(this.mailRepository.getObjectFromS3(sendingMessage.htmlContent), "UTF-8", storeMessage.htmlContent)
        val defaultBriefContentSize = this.secretManagerService.getEnv("MAX_MESSAGE_BRIEF_CONTENT_SIZE").toInt()
        storeMessage.briefContent = doc.text().byteInputStream().subString(defaultBriefContentSize)

        // Copy Html content and attachments to mail folder on s3
        this.mailRepository.storeSentEmailToS3(tenant, username, storeMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store sent message to s3 successfully")

        // Store email to dynamo db
        this.mailRepository.storeEmailToDatabase(mailAccount, DEFAULT_MAILBOX_SENT, storeMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store sent email to database successfully")

        // Update sent mailbox metric
        val mailBoxPk = "${TENANT_INDEX}#${mailAccount.tenant}#${USER_INDEX}#${mailAccount.username}#${MAIL_ACCOUNT_INDEX}#${mailAccount.email}#${MAILBOX_INDEX}"
        val mailBoxSk = "${MAILBOX_INDEX}#${DEFAULT_MAILBOX_SENT}"
        this.mailRepository.updateMailBoxMetric(mailBoxPk, mailBoxSk, storeMessage.size, 1, 0)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Update SENT mail box metric successfully")
        return storeMessage
    }

    /**
     * This method perform sync message from provider, currently only supported the sync message from
     * the mailbox INBOX
     *
     * The method perform following steps:
     * Step 1: Get the current mail account information
     * Step 2: Get the target MailBox to sync
     * Step 3: Mark the sync status to SYNCING to prevent duplicate sync
     * Step 4: Try to sync new email from the last sync time
     * Step 5: Save the new synced messages to DynamoDB and S3
     * Step 6: Mark the sync status to SYNCED
     * Step 7: Save the last sync time to the database
     * Step 8: Update the mailbox size
     * Step 9: If sync error send Sync Email failed notification, and mark the sync status to SYNC_ERR
     *
     * @param data: MailMessageQueueData the event data which received from the Mail Queue
     * @return
     * @exception MailBoxIsStillSyncing Raise exception when MailBox currently is syncing the message
     */
    @Throws(MailBoxIsStillSyncing::class, Exception::class)
    suspend fun syncEmail(data: MailMessageQueueData) {
        val (tenant, username, _, arguments) = data
        val box = arguments["mailbox"]?.toString()?.uppercase()
        val account = arguments["email"]?.toString()
        if (box.isNullOrEmpty() || account.isNullOrEmpty())
            throw InvalidArguments("Missing mailbox or mail account")

        // Get the mail account information
        val mailAccount = this.mailRepository.getMailAccount(tenant, username, account)
            ?: throw NotFoundException("Mail account [$account] not found!, tenant [$tenant] username [$username]")

        // Get the mailbox information
        var mailBox: MailBox
        SyncMessageLock.mutex.withLock {
            mailBox = this.mailRepository.getMailBox(mailAccount, box)
                ?: throw NotFoundException("MailBox [$box] not found!, tenant [$tenant] username [$username] account [$account]")
            // Return intermediately when current MailBox is still syncing
            if (mailBox.syncStatus == MailBoxSyncStatus.SYNCING.toString()) {
                throw MailBoxIsStillSyncing("MailBox [${mailBox.name}] is still syncing!")
            }
            // Mark the sync status to SYNCING to prevent duplicate sync
            this.mailRepository.changeMailBoxStatus(mailBox, MailBoxSyncStatus.SYNCING)
        }

        var totalSize: Long = 0
        var messageProcessedCount = 0
        var lastSyncTime: Date? = null
        var mailBoxSyncStatus = MailBoxSyncStatus.SYNCING
        val firstSync = mailBox.lastMailboxSync == null

        // Try to sync new email from the last sync time
        try {
            val messages = this.mailRepository.getLastMailBoxSync(mailAccount, mailBox)
            logger.info("${mailAccount.inboundType.uppercase()} [$mailBox] found ${messages.size} new message")

            // Save Email to DynamoDB and S3
            for (message in messages) {
                // Convert message received from provider to email
                val mailMessage = this.mailRepository.toEmail(mailAccount, message)
                // Save the email information to database
                this.mailRepository.storeEmailToDatabase(mailAccount, mailBox.name, mailMessage)
                // Send the new email notification to AppSync
                this.mailRepository.notifyNewEmailReceived(mailAccount, mailBox, mailMessage, firstSync)
                // Calculate the total size of new message received
                totalSize += mailMessage.size
                messageProcessedCount = messageProcessedCount.inc()
                (message.receivedDate ?: message.sentDate)?.let {
                    lastSyncTime = it
                }
            }
            // Mark the sync status to SYNC successfully
            mailBoxSyncStatus = MailBoxSyncStatus.SYNCED
        } catch (e: Exception) {
            logger.error("Sync Email failed: message ${e.message}")
            // Mark the sync status to SYNC ERROR
            mailBoxSyncStatus = MailBoxSyncStatus.SYNC_ERR
            // Send the sync email error notification to AppSync
            this.mailRepository.notifySyncEmailError(
                mailAccount,
                mailBox,
                "Sync Email failed with message: ${e.message}"
            )
            throw e
        } finally {
            // Update the mailbox last mailbox sync time
            this.mailRepository.updateMailBoxLastSyncTime(mailBox, lastSyncTime)
            // Update the mailbox size, count, and unread count
            this.mailRepository.updateMailBoxMetric(mailBox.pk, mailBox.sk, totalSize, messageProcessedCount, messageProcessedCount)
            // Change status of mailbox
            this.mailRepository.changeMailBoxStatus(mailBox, mailBoxSyncStatus)
        }
    }

    /**
     * This method use to link email to the TO systems
     *
     * @param mailAccount The mail account information
     * @param authenticatedUser The user is decoded from cognito token
     * @return void
     * @exception
     */
    fun linkEmail(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser) = try {
        // Validate mail account information by this step
        mailRepository.validateMailAccount(mailAccount, authenticatedUser)
        // Encrypt password
        val encryptionKey = this.secretManagerService.getAWSSecretValue(Constant.ENCRYPT_SECRET_KEY)
        // Save mail account information to dynamodb
        mailRepository.linkEmail(mailAccount, authenticatedUser, encryptionKey)
        logger.info("Link mail account ${mailAccount.email} successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }


    /**
     * Get mail boxes for given [mail] which related to [authenticatedUser]
     * The user [authenticatedUser] contains **tenant** and **username** values
     *
     * Returns list of mail boxes information with exposed data
     */
    fun getMailBoxes(mail: String, authenticatedUser: AuthenticatedUser): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            return mailRepository
                .getMailBoxes(tenant, username, mail)
                .sortedWith(
                    // Sort by following order INBOX > SENT > DRAFT > SPAM > DELETE > OTHERS
                    compareBy(
                        { it.name == DEFAULT_MAILBOX_DELETE },
                        { it.name == DEFAULT_MAILBOX_SPAM },
                        { it.name == DEFAULT_MAILBOX_DRAFT },
                        { it.name == DEFAULT_MAILBOX_SENT },
                        { it.name == DEFAULT_MAILBOX_INBOX }
                    )
                )
                .map { it.toJson() }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get mail accounts for [authenticatedUser]
     *
     * Returns list of mail accounts  information with exposed data
     */
    fun getUserMailAccounts(authenticatedUser: AuthenticatedUser): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            return mailRepository
                .getUserMailAccounts(tenant, username)
                .map { it.toJson() }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Generate presigned url to put [fileName] with [contentType] to s3 bucket
     * Return [PresignedUrlResponsePayload] object
     */
    fun generatePresignedUrl(fileName: String, contentType: String): PresignedUrlResponsePayload = try {
        mailRepository.generatePresignedUrl(fileName, contentType)
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }

    /**
     * Get mail boxes for given [getMailParams] which related to [authenticatedUser]
     * The user [authenticatedUser] contains **tenant** and **username** values
     *
     * Returns list of thread mail messages information with exposed data
     */
    fun getMailMessagesFromMailBox(
        getMailParams: GetMailRequestParams,
        authenticatedUser: AuthenticatedUser
    ): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
            // Mark the mail message as SEEN
            mailRepository.changeMailMessageStatus(
                tenant, username, getMailParams,
                Flags(Flags.Flag.RECENT), Flags(Flags.Flag.SEEN)
            )
            return mailRepository.getThreadMails(tenant, username, getMailParams).map { it.toJsonWithCDN(mailCDN) }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get [mailbox] mails of [mail] address for given [authenticatedUser] returned from Cognito
     *
     * Returns [List] of [Map] with exposed data
     */
    fun getMailsInsideMailBox(
        authenticatedUser: AuthenticatedUser,
        mail: String,
        mailbox: String,
        token: String?
    ): Map<String, Any> {
        try {
            val (tenant, username) = authenticatedUser
            val (lastEvaluatedKey, emails) = mailRepository.getMailsInsideMailBox(
                tenant,
                username,
                mail,
                mailbox,
                token
            )

            val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
            return mapOf(
                "token" to lastEvaluatedKey,
                "mails" to emails.map { it.toJsonWithCDN(mailCDN) }
            )
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Change mailbox for given email with data [changeMailboxData] of user [authenticatedUser]
     * Two types will be supported:
     * - Change mailbox for individual message
     * - Change mailbox for list of messages
     */
    @Throws(MailBoxNotExisted::class, Exception::class)
    fun changeMailbox(authenticatedUser: AuthenticatedUser, changeMailboxData: ChangeMailboxRequestBody) {
        try {
            // Extract data
            val (tenant, username) = authenticatedUser
            val (from, to, mail, messages) = changeMailboxData

            // Get mailboxes that belongs to user's mail
            val mailboxes = mailRepository.getMailBoxes(tenant, username, mail)
            val strMailBoxes = mailboxes.map { it.name.uppercase() }

            // Check from, to mailboxes existed
            if (!strMailBoxes.contains(from.uppercase()) || !strMailBoxes.contains(to.uppercase())) {
                throw MailBoxNotExisted("Mailbox parameter $from or $to is not existed")
            }

            // Start moving between mailboxes
            if (messages != null) {
                if (messages.size == 1) {
                    // Change mailbox for individual message
                    val message = messages.first()
                    mailRepository.changeMailboxOfMessage(tenant, username, from, to, mail, message)
                } else {
                    // Batch change mailbox for list of messages
                    mailRepository.changeMailboxOfMessages(tenant, username, from, to, mail, messages)
                }

            }

        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }
}
