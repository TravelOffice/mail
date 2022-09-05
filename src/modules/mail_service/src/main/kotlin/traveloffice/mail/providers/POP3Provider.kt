package traveloffice.mail.providers

import org.springframework.stereotype.Repository
import traveloffice.mail.extensions.between
import traveloffice.mail.entities.MailAccount
import java.util.*
import javax.mail.Message

@Repository
class POP3Provider : MailProvider() {

    /**
     * This method perform create the mailbox property
     *
     * @param mailAccount: The mail account information.

     * @return The property of the POP3 Mail.
     * @exception
     */
    @Override
    override fun getProperty(mailAccount: MailAccount): Properties {
        //create properties field
        val properties = Properties()
        properties["mail.pop3.host"] = mailAccount.inboundHost
        properties["mail.pop3.port"] = mailAccount.inboundPort
        properties["mail.pop3.ssl.enable"] = "true"
        // TODO: Need to implement wait time out for connect to provider
        properties["mail.pop3.timeout"] = "10000"
        properties["mail.pop3s.timeout"] = "10000"
        properties["mail.pop3s.connectiontimeout"] = "10000"
        return properties
    }

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
        endDate: Date?,
    ): List<Message> {
        // POP3 not supported filter message by date time, so we get all the message and filter later
        val messages = this.getMessages(mailAccount, mailBox)
        val filterMessages = mutableListOf<Message>()
        val maxDate = endDate ?: Date()
        for(i in messages.size - 1 downTo 0) {
            var message = messages[i]
            if (!message.sentDate.between(startDate, maxDate)) {
                break
            }
            filterMessages.add(message)
        }
        return filterMessages
    }
}