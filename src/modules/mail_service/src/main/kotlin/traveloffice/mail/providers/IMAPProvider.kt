package traveloffice.mail.providers

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPMessage
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailMessage
import java.io.InputStream
import javax.mail.Message
import javax.mail.Store

class IMAPProvider : MailProvider() {

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
        val folder = rawMessages.first().folder as IMAPFolder
        val messages = rawMessages.map {
            val message = MailMessage()
            message.uid = folder.getUID(it).toString()
            message.raw = it
            return@map message
        }
        val sortedMessages = messages.sortedByDescending { it.uid!!.toLong() }
        if (limit != null) {
            return sortedMessages.stream().limit(limit).toList()
        }
        return sortedMessages
    }

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param syncedMessagesUID: All UIDs of synced messages
     * @param syncedMessagesFailedUID: All UIDs of sync failed messages
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
        val lastSyncedUID = syncedMessagesUID.first().toLong()
        val retryMessages = allMessages.filter { syncedMessagesFailedUID.contains(it.uid!!) }.toMutableList()
        retryMessages.addAll(allMessages.filter { it.uid!!.toLong() > lastSyncedUID })
        return retryMessages
    }

    /**
     * This method perform to get the mime input stream from the IMAP message
     *
     * @param message: The message information.

     * @return The property of the InputStream.
     * @exception
     */
    @Override
    override fun getMessageInputStream(message: Message): InputStream = (message as IMAPMessage).mimeStream

    /**
     * This method perform to get the message ID from IMAP
     *
     * @param message: The message information.

     * @return The ID of the message.
     * @exception
     */
    @Override
    override fun getMessageId(message: Message): String? = (message as IMAPMessage).messageID

    /**
     * This method perform to get the message reply to from provider
     *
     * @param message: The message information.

     * @return The In-Reply-To of the message.
     * @exception
     */
    @Override
    override fun getInRelyTo(message: Message): String? = (message as IMAPMessage).inReplyTo

    /**
     * This method perform to validate whether the provider supported UIDL or not
     * @param store The mail store information
     * @return true if supported and otherwise false
     */
    @Override
    override fun isSupportUIDL(store: Store): Boolean = true
}
