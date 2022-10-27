package traveloffice.mail.providers

import com.sun.mail.pop3.POP3Folder
import com.sun.mail.pop3.POP3Message
import com.sun.mail.pop3.POP3Store
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailMessage
import traveloffice.mail.extensions.indexOf
import java.io.InputStream
import javax.mail.Message
import javax.mail.Store

class POP3Provider : MailProvider() {

    private var logger: Logger = LoggerFactory.getLogger(POP3Provider::class.java)

    /**
     * This method perform to fetch all message from the provider
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @return The list of matching messages
     * @exception
     */
    @Override
    override fun getMessages(mailAccount: MailAccount, mailBox: String, limit: Long?): List<MailMessage> {
        val properties = this.getProperty(mailAccount)
        val rawMessages = this.getMessagesFromMailBox(properties, mailBox, mailAccount)
        val folder = rawMessages.first().folder as POP3Folder
        val messages = rawMessages.map {
            val message = MailMessage()
            message.uid = folder.getUID(it)
            message.raw = it
            return@map message
        }.reversed()
        if (limit != null) {
            return messages.stream().limit(limit).toList()
        }
        return messages
    }

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param syncedMessagesUID: All UIDs of synced messages
     * @return The list of matching messages
     * @exception
     */
    @Override
    override fun getMessages(
        mailAccount: MailAccount,
        mailBox: String,
        syncedMessagesUID: List<String>,
        syncedMessagesFailedUID: List<String>,
        lastSyncTime: Long?
    ): List<MailMessage> {
        val allMessages = this.getMessages(mailAccount, mailBox)
        val retryMessages = allMessages.filter { syncedMessagesFailedUID.contains(it.uid) }.toMutableList()
        // Find the last synced message number
        val lastSyncMessageNumber =
            allMessages.indexOf { message -> syncedMessagesUID.indexOf { message.uid == it } >= 0 }
        if (lastSyncMessageNumber >= 0) {
            retryMessages.addAll(allMessages.subList(0, lastSyncMessageNumber))
        } else if (lastSyncTime != null) {
            logger.info("Not found any last synced message by UID, try to find by syncTime")
            val filterMessages = mutableListOf<MailMessage>()
            for (message in allMessages) {
                if (message.raw!!.sentDate.time <= lastSyncTime) {
                    break
                }
                filterMessages.add(message)
            }
            retryMessages.addAll(filterMessages)
        } else {
            logger.error("Not found any last synced message in list of remote message, continue to get all new remote message")
            retryMessages.addAll(allMessages)
        }
        return retryMessages
    }

    /**
     * This method perform to get the mime input stream from the POP3 message
     *
     * @param message: The message information.

     * @return The property of the InputStream.
     * @exception
     */
    @Override
    override fun getMessageInputStream(message: Message): InputStream = (message as POP3Message).mimeStream

    /**
     * This method perform to get the message ID from POP3
     *
     * @param message: The message information.

     * @return The ID of the message.
     * @exception
     */
    @Override
    override fun getMessageId(message: Message): String? = (message as POP3Message).messageID

    /**
     * This method perform to get the message reply to from provider
     *
     * @param message: The message information.

     * @return The In-Reply-To of the message.
     * @exception
     */
    @Override
    override fun getInRelyTo(message: Message): String? = (message as POP3Message).getHeader("In-Reply-To")?.first()

    /**
     * This method perform to validate whether the provider supported UIDL or not
     * @param store The mail store information
     * @return true if supported and otherwise false
     */
    @Override
    override fun isSupportUIDL(store: Store): Boolean {
        val capabilities = (store as POP3Store).capabilities()
        return (capabilities == null || capabilities.containsKey("UIDL"))
    }
}
