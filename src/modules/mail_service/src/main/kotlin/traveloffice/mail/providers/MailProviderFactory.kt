package traveloffice.mail.providers

import org.springframework.stereotype.Repository
import traveloffice.mail.exceptions.UnSupportedInboxType

@Repository
class MailProviderFactory {
    fun makeProvider(type: String): MailProvider {
        return when(type) {
            "imap"-> IMAPProvider()
            "pop3" -> POP3Provider()
            else -> throw UnSupportedInboxType("Unsupported action event $type")
        }
    }
}
