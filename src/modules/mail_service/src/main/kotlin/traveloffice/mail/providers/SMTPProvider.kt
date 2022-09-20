package traveloffice.mail.providers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.MailMessage
import java.util.*
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart


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
            message.setFrom(mailMessage.fromAsInternetAddress)
            message.setRecipients(
                Message.RecipientType.TO, mailMessage.toAsInternetAddresses.toTypedArray()
            )
            message.subject = mailMessage.subject
            // Add message content
            val mimeBodyPart = MimeBodyPart()
            mimeBodyPart.setContent(mailMessage.htmlContent, "text/html; charset=utf-8")
            val multipart: Multipart = MimeMultipart()
            multipart.addBodyPart(mimeBodyPart)
            message.setContent(multipart)
            Transport.send(message)
            logger.info("Send email successfully")
        } catch (e: MessagingException) {
            logger.error("Failed to send email: ", e)
        }
    }

    /**
     * This method performs add attachments to mail
     * @param multipart Multipart is a container that holds multiple body parts
     * @param filename Name of file on s3 bucket
     * @return void
     */
    private fun addAttachment(multipart: Multipart, filename: String) {
        //TOTO: Need to get file from s3 bucket
        val source: DataSource = FileDataSource(filename)
        val messageBodyPart: BodyPart = MimeBodyPart()
        messageBodyPart.dataHandler = DataHandler(source)
        messageBodyPart.fileName = filename
        multipart.addBodyPart(messageBodyPart)
    }
}