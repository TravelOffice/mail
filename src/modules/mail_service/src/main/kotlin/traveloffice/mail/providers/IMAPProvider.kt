package traveloffice.mail.providers

import com.sun.mail.imap.IMAPMessage
import com.sun.mail.pop3.POP3Message
import traveloffice.mail.entities.MailAccount
import java.io.InputStream
import java.util.*
import javax.mail.Folder
import javax.mail.Message
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm

class IMAPProvider : MailProvider() {

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param startDate: The start date
     * @param endDate: The end date
     * @return The list of matching messages
     * @exception
     */
    @Override
    override fun getMessages(mailAccount: MailAccount, mailBox: String, startDate: Date, endDate: Date?): List<Message> {
        val properties = this.getProperty(mailAccount)
        val store = this.emailStoreConnect(properties, mailAccount)
        val emailFolder = this.getMailBox(store, mailBox, Folder.READ_ONLY)
        var term: SearchTerm?
        val maxDate = endDate ?: Date()
        val minDateTerm = ReceivedDateTerm(ComparisonTerm.GE, startDate)
        val maxDateTerm = ReceivedDateTerm(ComparisonTerm.LE, maxDate)
        term = minDateTerm
        term = AndTerm(term, maxDateTerm)

        val messages = emailFolder.search(term).toList()
        // Due to the GT start date return wrong message, so compare the receivedDate to return exact message
        var count = 0
        for (message in messages) {
            if (message.receivedDate.after(startDate)) break
            count++
        }
        return messages.slice(count until messages.size).toList()
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
    override fun getMessageId(message: Message): String = (message as IMAPMessage).messageID

    /**
     * This method perform to get the message reply to from provider
     *
     * @param message: The message information.

     * @return The In-Reply-To of the message.
     * @exception
     */
    @Override
    override fun getInRelyTo(message: Message): String? = (message as IMAPMessage).inReplyTo
}
