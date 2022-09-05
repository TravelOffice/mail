package traveloffice.mail.repositories

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailInboundType
import traveloffice.mail.exceptions.UnSupportedInboxType
import traveloffice.mail.providers.IMAPProvider
import traveloffice.mail.providers.POP3Provider
import java.util.*
import javax.mail.Message

@Repository
@DependsOn(value = ["SecretManager"])
class MailRepository {
    @Autowired
    lateinit var imapProvider: IMAPProvider

    @Autowired
    lateinit var pop3Provider: POP3Provider

    var logger: Logger = LoggerFactory.getLogger(MailRepository::class.java)


    /**
     * This method perform get new message from the last sync
     *
     * The method perform following steps:
     * Step 1: Get the mailBox inbound Type, currently only supported IMAP and POP3
     * Step 2: Get the last mailbox synced time
     * Step 3: Get the message from the provider
     *
     * @param mailAccount: The mail account information.
     * @param mailBox: The mailbox which use to fetch message from
     * @return List of new message from the last sync
     * @exception UnSupportedInboxType
     */
    fun getLastMailBoxSync(mailAccount: MailAccount, mailBox: String): List<Message> {
        logger.info("getLastMailBoxSync MailBox[$mailBox] with MailAccount [$mailAccount]")
        when (mailAccount.inboundType) {
            MailInboundType.pop3.toString(), MailInboundType.pop3s.toString() -> {
                val lastMailboxSync = mailAccount.lastMailboxSync
                return if (lastMailboxSync == null) {
                    // Get all message for the first sync
                    this.pop3Provider.getMessages(mailAccount, mailBox)
                } else {
                    val lastSync = Date(lastMailboxSync)
                    // Get all message from the last sync to now
                    this.pop3Provider.getMessages(mailAccount, mailBox, lastSync, null)
                }
            }

            MailInboundType.imap.toString(), MailInboundType.imaps.toString() -> {
                val lastMailboxSync = mailAccount.lastMailboxSync
                return if (lastMailboxSync == null) {
                    // Get all message for the first sync
                    this.imapProvider.getMessages(mailAccount, mailBox)
                } else {
                    val lastSync = Date(lastMailboxSync)
                    // Get all message from the last sync to now
                    this.imapProvider.getMessages(mailAccount, mailBox, lastSync, null)
                }
            }

            else -> {
                throw UnSupportedInboxType("Unsupported action event ${mailAccount.inboundType}")
            }
        }
    }
}
