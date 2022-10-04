package traveloffice.mail.providers

import com.sun.mail.util.MailConnectException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAIL_TIMEOUT
import traveloffice.mail.common.Constant.Companion.ERROR_CONNECT_TO_OUTBOUND_SERVER
import traveloffice.mail.common.Constant.Companion.ERROR_EMAIL_ADDRESS_IS_INVALID
import traveloffice.mail.common.Constant.Companion.ERROR_LOGIN_TO_OUTBOUND_SERVER
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailMessage
import traveloffice.mail.exceptions.ResponseException
import java.io.InputStream
import java.util.*
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.*
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource


@Repository
class SMTPProvider {
    var logger: Logger = LoggerFactory.getLogger(SMTPProvider::class.java)

    /**
     * This method performs to authenticate outgoing server information
     * @param mailAccount The mail account information
     * @return The mail session after authentication successfully
     */
    private fun authenticate(mailAccount: MailAccount): Session {
        val prop = Properties()
        prop["mail.smtp.host"] = mailAccount.outboundHost
        prop["mail.smtp.port"] = mailAccount.outboundPort
        prop["mail.smtp.auth"] = "true"
        prop["mail.smtp.timout"] = DEFAULT_MAIL_TIMEOUT
        prop["mail.smtp.connectiontimeout"] = DEFAULT_MAIL_TIMEOUT
        prop["mail.smtp.writetimeout"] = DEFAULT_MAIL_TIMEOUT
        when (mailAccount.outboundSslMode) {
            "ssl/tls" -> prop["mail.smtp.ssl.enable"] = "true" //SSL
            "starttls" -> prop["mail.smtp.starttls.enable"] = "true" //TLS
        }
        return Session.getInstance(prop, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(mailAccount.outboundUser, mailAccount.outboundPassword)
            }
        })
    }

    /**
     * This method performs to send mail
     * @param mailAccount The mail account information
     * @param mailMessage THe sending mail information
     * @return void
     */
    fun sendMail(mailAccount: MailAccount, mailMessage: MailMessage) {
        try {
            val session = this.authenticate(mailAccount)
            val message: Message = MimeMessage(session)
            // Set from, to, cc, bcc email addresses
            message.setFrom(mailMessage.fromAsInternetAddress)
            message.setRecipients(
                Message.RecipientType.TO, mailMessage.toAsInternetAddresses.toTypedArray()
            )
            message.setRecipients(
                Message.RecipientType.CC, mailMessage.ccAsInternetAddresses.toTypedArray()
            )
            message.setRecipients(
                Message.RecipientType.BCC, mailMessage.bccAsInternetAddresses.toTypedArray()
            )
            message.subject = mailMessage.subject
            // Add message content
            val multipart: Multipart = MimeMultipart()
            this.addBodyPart(multipart, mailMessage.htmlContentInputStream, "text/html; charset=utf-8")
            // Add message attachment
            mailMessage.attachments?.forEach {
                this.addBodyPart(multipart, it.inputStream, it.mimeType, it.name)
            }
            message.setContent(multipart)
            // Send mail
            Transport.send(message)
        } catch (e: MailConnectException) {
            throw ResponseException(e.message.toString(), ERROR_CONNECT_TO_OUTBOUND_SERVER)
        } catch (e: AuthenticationFailedException) {
            throw ResponseException(e.message.toString(), ERROR_LOGIN_TO_OUTBOUND_SERVER)
        } catch (e: SendFailedException) {
            throw ResponseException(e.message.toString(), ERROR_EMAIL_ADDRESS_IS_INVALID)
        } catch (e: Exception) {
            logger.error(e.stackTraceToString())
        }
    }

    private fun addBodyPart(multipart: Multipart, inputStream: InputStream, mimeType: String, fileName: String = "") {
        val messageBodyPart: BodyPart = MimeBodyPart()
        val dataSrc: DataSource = ByteArrayDataSource(inputStream.readAllBytes(), mimeType)
        messageBodyPart.dataHandler = DataHandler(dataSrc)
        messageBodyPart.fileName = fileName
        multipart.addBodyPart(messageBodyPart)
    }
}
