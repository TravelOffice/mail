package traveloffice.mail.services

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailBoxSyncStatus
import traveloffice.mail.entities.MailMessageQueueData
import traveloffice.mail.exceptions.FunctionNotImplemented
import traveloffice.mail.exceptions.MailBoxIsStillSyncing
import traveloffice.mail.repositories.MailRepository

@Service
@DependsOn("DynamoDBMapper")
class MailService {
    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

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
     *         TODO: Currently only support mailbox INBOX
     * Step 3: Mark the sync status to SYNCING to prevent duplicate sync
     * Step 4: Try to sync new email from the last sync time
     * Step 5: TODO: Need to implement the action to save new synced messages to DynamoDB and S3
     * Step 6: Mark the sync status to SYNCED
     * Step 7: TODO: Save the last sync time to the database
     *
     * @param data: MailMessageQueueData the event data which received from the Mail Queue
     * @return
     * @exception MailBoxIsStillSyncing Raise exception when MailBox currently is syncing the message
     */
    @Throws(MailBoxIsStillSyncing::class, Exception::class)
    fun syncEmail(data: MailMessageQueueData) {
        // Get the mail account information
        val mailAccount = this.dynamoDBMapper.load(
            MailAccount::class.java,
            "${TENANT_INDEX}#${data.tenant}",
            "${MAIL_ACCOUNT_INDEX}#${data.username}"
        )

        // TODO: Currently only support mailbox INBOX
        val mailBox = "INBOX"

        // Return intermediately when current MailBox is still syncing
        if (mailAccount.syncStatus == MailBoxSyncStatus.SYNCING.toString()) {
            throw MailBoxIsStillSyncing("MailBox is still syncing!")
        }

        // Mark the sync status to SYNCING to prevent duplicate sync
        mailAccount.syncStatus = MailBoxSyncStatus.SYNCING.toString()
        this.dynamoDBMapper.save(mailAccount)

        // Try to sync new email from the last sync time
        try {
            val messages = this.mailRepository.getLastMailBoxSync(mailAccount, mailBox)
            logger.info("${mailAccount.inboundType.uppercase()} [$mailBox] found ${messages.size} new message")
        } catch (e: Exception) {
            throw e
        } finally {
            mailAccount.syncStatus = MailBoxSyncStatus.SYNCED.toString()
            this.dynamoDBMapper.save(mailAccount)
        }
    }
}