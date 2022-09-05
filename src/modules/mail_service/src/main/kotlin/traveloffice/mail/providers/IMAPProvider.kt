package traveloffice.mail.providers

import traveloffice.mail.entities.MailAccount
import java.util.*
import javax.mail.Folder
import javax.mail.Message
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm

class IMAPProvider : MailProvider() {

    /**
     * This method perform create the mailbox property
     *
     * @param mailAccount: The mail account information.

     * @return The property of the IMAP Mail.
     * @exception
     */
    @Override
    override fun getProperty(mailAccount: MailAccount): Properties {
        //create properties field
        val properties = Properties()
        properties["mail.imap.host"] = mailAccount.inboundHost
        properties["mail.imap.port"] = mailAccount.inboundPort
        properties["mail.imap.ssl.enable"] = "true"
        return properties
    }


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
    override fun getMessages(
        mailAccount: MailAccount,
        mailBox: String,
        startDate: Date,
        endDate: Date?
    ): List<Message> {
        //create properties field
        val properties = Properties()
        properties["mail.imap.host"] = mailAccount.inboundHost
        properties["mail.imap.port"] = mailAccount.inboundPort
        // TODO: Need to implement wait time out for connect to provider
        properties["mail.imap.timeout"] = "10000"
        properties["mail.imap.ssl.enable"] = "true"

        var term: SearchTerm?
        val maxDate = endDate ?: Date()
        var minDateTerm = ReceivedDateTerm(ComparisonTerm.GE, startDate)
        val maxDateTerm = ReceivedDateTerm(ComparisonTerm.LE, maxDate)
        term = minDateTerm
        term = AndTerm(term, maxDateTerm)

        val store = this.emailStoreConnect(properties, mailAccount)
        val emailFolder = this.getMailBox(store, mailBox, Folder.READ_ONLY)

        return emailFolder.search(term).toList()
    }
}