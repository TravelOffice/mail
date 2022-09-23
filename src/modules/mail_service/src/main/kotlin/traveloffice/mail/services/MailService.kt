package traveloffice.mail.services

import com.amazonaws.services.kms.model.NotFoundException
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import traveloffice.mail.common.Constant
import traveloffice.mail.common.SyncMessageLock
import traveloffice.mail.entities.*
import traveloffice.mail.exceptions.FunctionNotImplemented
import traveloffice.mail.exceptions.InvalidArguments
import traveloffice.mail.exceptions.MailBoxIsStillSyncing
import traveloffice.mail.repositories.MailRepository
import java.util.*

@Service
@DependsOn("DynamoDBMapper")
class MailService {
    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var mailRepository: MailRepository

    var logger: Logger = LoggerFactory.getLogger(MailRepository::class.java)

    /**
     * This method use to send the email to the provider
     *
     * @param data: MailMessageQueueData the event data which received from the Mail Queue
     * @return
     * @exception
     */
    fun sendEmail(data: MailMessageQueueData) {
        // TODO: Send Email will be implemented later
        throw FunctionNotImplemented("Send Email will be implemented later")
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

        var totalSize = 0
        var messageProcessedCount = 0
        var lastSyncTime: Date? = null
        var mailBoxSyncStatus = MailBoxSyncStatus.SYNCING
        var firstSync = mailBox.lastMailboxSync == null

        // Try to sync new email from the last sync time
        try {
            val messages = this.mailRepository.getLastMailBoxSync(mailAccount, mailBox)
            logger.info("${mailAccount.inboundType.uppercase()} [$mailBox] found ${messages.size} new message")

            // Save Email to DynamoDB and S3
            for (message in messages) {
                // Convert message received from provider to email
                val mailMessage = this.mailRepository.toEmail(mailAccount, message)
                // Save the email information to database
                this.mailRepository.storeEmailToDatabase(mailAccount, mailBox, mailMessage)
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
            this.mailRepository.updateMailBoxMetric(mailBox, totalSize, messageProcessedCount, messageProcessedCount)
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
                .map { it -> it.restrict() }
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
                .map { it -> it.restrict() }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * This method performs generate presigned url
     *
     * @params fileName The name of file that will be uploaded to s3
     * @return PresignedUrlResponsePayload object
     */
    fun generatePresignedUrl(fileName: String): PresignedUrlResponsePayload = try {
        mailRepository.generatePresignedUrl(fileName)
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }
}
