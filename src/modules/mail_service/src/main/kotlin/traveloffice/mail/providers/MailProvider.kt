package traveloffice.mail.providers

import traveloffice.mail.entities.MailAccount
import java.util.*
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store

abstract class MailProvider {
    /**
     * This method perform create the mailbox property
     *
     * @param mailAccount: The mail account information.

     * @return The property of the EMail.
     * @exception
     */
    abstract fun getProperty(mailAccount: MailAccount): Properties

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param startDate: The start date
     * @param endDate: The end date
     * @return The list of messages.
     * @exception
     */
    abstract fun getMessages(
        mailAccount: MailAccount,
        mailBox: String,
        startDate: Date,
        endDate: Date?
    ): List<Message>

    /**
     * This method perform to fetch all message from the provider
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @return The list of matching messages
     * @exception
     */
    fun getMessages(mailAccount: MailAccount, mailBox: String): List<Message> {
        val properties = this.getProperty(mailAccount)
        return this.getMessagesFromMailBox(properties, mailBox, mailAccount)
    }

    protected fun emailStoreConnect(properties: Properties, mailAccount: MailAccount): Store {
        val emailSession = Session.getDefaultInstance(properties)
        //create the IMAP/POP3 store object and connect with the pop server
        val store = emailSession.getStore(mailAccount.inboundType)
        // TODO: Need to implement encrypt/decrypt password later
        store.connect(mailAccount.inboundHost, mailAccount.inboundUser, mailAccount.inboundPassword)
        return store
    }

    protected fun emailStoreDisconnect(store: Store) {
        return store.close()
    }

    protected fun getMailBox(store: Store, mailBox: String, openMode: Int): Folder {
        //create the folder object and open it
        val emailFolder: Folder = store.getFolder(mailBox)
        emailFolder.open(openMode)
        return emailFolder
    }

    protected fun closeMailBox(folder: Folder) {
        return folder.close()
    }

    protected fun getMessagesFromMailBox(
        properties: Properties,
        mailBox: String,
        mailAccount: MailAccount
    ): List<Message> {
        val store = this.emailStoreConnect(properties, mailAccount)
        val emailFolder = this.getMailBox(store, mailBox, Folder.READ_ONLY)

        return emailFolder.messages.toList()
    }
}