package traveloffice.mail

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import traveloffice.mail.entities.MailMessageQueueAction
import traveloffice.mail.entities.MailMessageQueueEvent
import traveloffice.mail.exceptions.MalformedMessageEventData
import traveloffice.mail.exceptions.UnSupportedEvent
import traveloffice.mail.services.MailMessageQueueService
import traveloffice.mail.services.MailService
import traveloffice.mail.services.SecretManagerService


@SpringBootApplication
class MailApplication: ApplicationRunner {
	@Autowired
	lateinit var secretManagerService: SecretManagerService
	@Autowired
	lateinit var messageQueueService: MailMessageQueueService
	@Autowired
	lateinit var mailService: MailService

	var logger: Logger = LoggerFactory.getLogger(MailApplication::class.java)

	override fun run(arg0: ApplicationArguments?) {
		val mailQueueEnabled: Boolean = secretManagerService.getEnv("MAIL_SERVICE_QUEUE_NAME").isNotEmpty()
		while (mailQueueEnabled) {
			try {
				val messages = this.messageQueueService.receiveMessage()
				if(!messages.isNullOrEmpty()) {
					logger.info("Receive message from message queue: ${Gson().toJson(messages)}")
					messages.forEach {
						this.handleMailMessageEvent(it)
					}
				}
			} catch (e: Exception) {
				logger.error(e.message)
			}
		}
	}

	/**
	 * This method handle mail message event from the Mail Queue
	 *
	 * @param event: MailMessageQueueEvent the event which received from the Mail Queue
	 * @return
	 * @exception UnSupportedEvent the unsupported event which receive from queue
	 * currently only supported Sync Mail event
	 */
	@Throws(UnSupportedEvent::class)
	private fun handleMailMessageEvent(event: MailMessageQueueEvent) {
		try {
			val messageData = event.data ?: throw MalformedMessageEventData("Malformed message event data $event")
			when(messageData.action) {
				MailMessageQueueAction.syncMessage.toString() -> {
					mailService.syncEmail(messageData)
				}
				else -> {
					throw UnSupportedEvent("Unsupported action event ${messageData.action}")
				}
			}
		} finally {
			// TODO: Need to do a check before deleting a message
			logger.info("Going to delete message from message queue: ${Gson().toJson(event)}")
			this.messageQueueService.deleteMessage(event.receiptHandle)
		}
	}
}

fun main(args: Array<String>) {
	runApplication<MailApplication>(*args)
}
