package traveloffice.mail.providers

import com.sun.mail.util.MailConnectException
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder
import org.apache.james.mime4j.parser.MimeStreamParser
import org.apache.james.mime4j.stream.MimeConfig
import tech.blueglacier.email.Email
import tech.blueglacier.parser.CustomContentHandler
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAIL_TIMEOUT
import traveloffice.mail.common.Constant.Companion.ERROR_CONNECT_TO_INBOUND_SERVER
import traveloffice.mail.common.Constant.Companion.ERROR_LOGIN_TO_INBOUND_SERVER
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailMessage
import traveloffice.mail.exceptions.ResponseException
import traveloffice.mail.exceptions.UnSupportedProvider
import java.io.InputStream
import java.util.*
import javax.mail.*


abstract class MailProvider {
    /**
     * This method perform create the mailbox property
     *
     * @param mailAccount: The mail account information.

     * @return The property of the POP3 Mail.
     * @exception
     */
    protected fun getProperty(mailAccount: MailAccount): Properties {
        //create properties field
        val properties = Properties()
        val inboundType = mailAccount.inboundType
        properties["mail.$inboundType.host"] = mailAccount.inboundHost
        properties["mail.$inboundType.port"] = mailAccount.inboundPort
        when (mailAccount.inboundSslMode) {
            "ssl/tls" -> properties["mail.$inboundType.ssl.enable"] = "true" //SSL
            "starttls" -> properties["mail.$inboundType.starttls.enable"] = "true" //TLS
        }
        properties["mail.$inboundType.timeout"] = DEFAULT_MAIL_TIMEOUT
        properties["mail.$inboundType.connectiontimeout"] = DEFAULT_MAIL_TIMEOUT
        return properties
    }

    /**
     * This method perform to fetch all message from the provider
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @return The list of matching messages
     * @exception
     */
    abstract fun getMessages(mailAccount: MailAccount, mailBox: String, limit: Long? = null): List<MailMessage>

    /**
     * This method perform to fetch the message from the start time to the end time
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @param syncedMessagesUID: The list of last synced UID message
     * @return The list of messages.
     * @exception
     */
    abstract fun getMessages(
        mailAccount: MailAccount,
        mailBox: String,
        syncedMessagesUID: List<String>,
        syncedMessagesFailedUID: List<String>,
        lastSyncTime: Long?
    ): List<MailMessage>

    /**
     * This method perform to get the mime input stream from the message
     *
     * @param message: The message information.

     * @return The property of the InputStream.
     * @exception
     */
    protected abstract fun getMessageInputStream(message: Message): InputStream

    /**
     * This method perform to get the message ID from provider
     *
     * @param message: The message information.

     * @return The ID of the message.
     * @exception
     */
    abstract fun getMessageId(message: Message): String?

    /**
     * This method perform to get the message reply to from provider
     *
     * @param message: The message information.

     * @return The In-Reply-To of the message.
     * @exception
     */
    abstract fun getInRelyTo(message: Message): String?

    private fun emailStoreConnect(properties: Properties, mailAccount: MailAccount): Store {
        val emailSession = Session.getDefaultInstance(properties)
        // Create the IMAP/POP3 store object and connect with the pop server
        val store = emailSession.getStore(mailAccount.inboundType)
        store.connect(mailAccount.inboundHost, mailAccount.inboundUser, mailAccount.inboundPassword)
        return store
    }

    private fun emailStoreDisconnect(store: Store) = store.close()

    private fun getMailBox(store: Store, mailBox: String, openMode: Int): Folder {
        //create the folder object and open it
        val emailFolder: Folder = store.getFolder(mailBox)
        emailFolder.open(openMode)
        return emailFolder
    }

    protected fun closeMailBox(folder: Folder) = folder.close()

    protected fun getMessagesFromMailBox(
        properties: Properties, mailBox: String, mailAccount: MailAccount
    ): List<Message> {
        val store = this.emailStoreConnect(properties, mailAccount)
        val emailFolder = this.getMailBox(store, mailBox, Folder.READ_ONLY)

        // Fetch only UID for filter message
        val fp = FetchProfile()
        fp.add(UIDFolder.FetchProfileItem.UID)
        val messages = emailFolder.messages
        emailFolder.fetch(messages, fp)
        return messages.toList()
    }

    /**
     * This method perform to parse message from IMAP or POP3 message
     *
     * @param message: The message information.
     *
     * @return The parsed email information
     * @exception
     */
    fun parseMessage(message: Message): Email {
        val contentHandler = CustomContentHandler()

        val mime4jParserConfig = MimeConfig.Builder()
            .setMaxHeaderLen(100000)
            .build()
        val bodyDescriptorBuilder = DefaultBodyDescriptorBuilder()
        val mime4jParser = MimeStreamParser(mime4jParserConfig, DecodeMonitor.SILENT, bodyDescriptorBuilder)
        mime4jParser.isContentDecoding = true
        mime4jParser.setContentHandler(contentHandler)

        val mailIn: InputStream = this.getMessageInputStream(message)
        mime4jParser.parse(mailIn)

        return contentHandler.email
    }

    /**
     * This method perform to validate the incoming server by connecting to mail server
     * @param mailAccount The mail account information
     * @return void
     */
    fun validateIncomingServer(mailAccount: MailAccount) {
        val properties = this.getProperty(mailAccount)
        try {
            val emailSession = Session.getInstance(properties)
            // Create the IMAP/POP3 store object and connect with the pop server
            val store = emailSession.getStore(mailAccount.inboundType)
            store.connect(mailAccount.inboundHost, mailAccount.inboundUser, mailAccount.inboundPassword)
            if (!this.isSupportUIDL(store))
                throw UnSupportedProvider("Provider not supported UIDL")
            this.emailStoreDisconnect(store)
        } catch (e: AuthenticationFailedException) {
            throw ResponseException("Failed to login to inbound server", ERROR_LOGIN_TO_INBOUND_SERVER)
        } catch (e: MailConnectException) {
            throw ResponseException(e.message.toString(), ERROR_CONNECT_TO_INBOUND_SERVER)
        }
    }

    /**
     * This method perform to validate whether the provider supported UIDL or not
     * @param store The mail store information
     * @return true if supported and otherwise false
     */
    protected abstract fun isSupportUIDL(store: Store): Boolean
}
