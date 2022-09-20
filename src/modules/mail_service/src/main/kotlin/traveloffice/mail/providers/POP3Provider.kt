package traveloffice.mail.providers

import com.sun.mail.pop3.POP3Message
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.extensions.between
import java.io.InputStream
import java.util.*
import javax.mail.Message

class POP3Provider : MailProvider() {

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param startDate: The start date
     * @param endDate: The end date
     * @return The property of the POP3 Mail.
     * @exception
     */
    @Override
    override fun getMessages(
        mailAccount: MailAccount,
        mailBox: String,
        startDate: Date,
        endDate: Date?
    ): List<Message> {
        // POP3 not supported filter message by date time, so we get all the message and filter later
        val messages = this.getMessages(mailAccount, mailBox)
        val filterMessages = mutableListOf<Message>()
        val maxDate = endDate ?: Date()
        for (i in messages.size - 1 downTo 0) {
            val message = messages[i]
            if (!message.sentDate.between(startDate, maxDate)) {
                break
            }
            filterMessages.add(message)
        }
        filterMessages.reverse()
        return filterMessages
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
    override fun getMessageId(message: Message): String = (message as POP3Message).messageID
}
