package traveloffice.mail.services

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.kms.model.NotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import traveloffice.mail.entities.MailBoxSyncStatus
import traveloffice.mail.entities.MailMessageQueueData
import traveloffice.mail.exceptions.FunctionNotImplemented
import traveloffice.mail.exceptions.InvalidArguments
import traveloffice.mail.exceptions.MailBoxIsStillSyncing
import traveloffice.mail.repositories.MailRepository

@Service
@DependsOn("DynamoDBMapper")
class MailService {
    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

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
     *
     * @param data: MailMessageQueueData the event data which received from the Mail Queue
     * @return
     * @exception MailBoxIsStillSyncing Raise exception when MailBox currently is syncing the message
     */
    @Throws(MailBoxIsStillSyncing::class, Exception::class)
    fun syncEmail(data: MailMessageQueueData) {
        val tenant = data.tenant
        val username = data.username
        val box = data.arguments["mailbox"]?.toString()?.uppercase()
        val account = data.arguments["mailAccount"]?.toString()
        if (box.isNullOrEmpty() || account.isNullOrEmpty())
            throw InvalidArguments("Missing mailbox or mail account")

        // Get the mail account information
        val mailAccount = this.mailRepository.getMailAccount(tenant, username, account)
            ?: throw NotFoundException("Mail account [$account] not found!, tenant [$tenant] username [$username]")

        // Get the mailbox information
        val mailBox = this.mailRepository.getMailBox(mailAccount, box)
            ?: throw NotFoundException("Mail box [$box] not found!, tenant [$tenant] username [$username] account [$account]")

        // Return intermediately when current MailBox is still syncing
        if (mailBox.syncStatus == MailBoxSyncStatus.SYNCING.toString()) {
            throw MailBoxIsStillSyncing("MailBox [${mailBox.name}] is still syncing!")
        }

        // Mark the sync status to SYNCING to prevent duplicate sync
        this.mailRepository.changeMailBoxStatus(mailBox, MailBoxSyncStatus.SYNCING)

        // Try to sync new email from the last sync time
        try {
            val messages = this.mailRepository.getLastMailBoxSync(mailAccount, mailBox)
            logger.info("${mailAccount.inboundType.uppercase()} [$mailBox] found ${messages.size} new message")
            var totalSize = 0
            var count = 0
            // Save Email to DynamoDB and S3
            for (message in messages) {
                // Convert message received from provider to email
                val mailMessage = this.mailRepository.toEmail(mailAccount, message)
                // Save the email information to database
                this.mailRepository.storeEmailToDatabase(mailAccount, mailBox, mailMessage)
                // Send the new email notification to AppSync
                this.mailRepository.notifyNewEmailReceived(mailAccount, mailBox, mailMessage)
                // Update the mailbox last mailbox sync time
                (mailMessage.receivedDate ?: mailMessage.sentDate)?.let {
                    this.mailRepository.updateMailBoxLastSyncTime(
                        mailBox,
                        it
                    )
                }
                // Calculate the total size of new message received
                totalSize += mailMessage.size
                count ++
            }
            // Update the mailbox size, count, and unread count
            this.mailRepository.updateMailBoxMetric(mailBox, totalSize, count, count)
        } catch (e: Exception) {
            // TODO: handle exception before rethrow
            logger.error("syncEmail failed: message ${e.message}")
            throw e
        } finally {
            this.mailRepository.changeMailBoxStatus(mailBox, MailBoxSyncStatus.SYNCED)
        }
    }
}
