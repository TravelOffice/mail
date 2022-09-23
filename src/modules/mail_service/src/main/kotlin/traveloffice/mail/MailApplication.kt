package traveloffice.mail

import com.google.gson.Gson
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import traveloffice.mail.entities.MailMessageQueueAction
import traveloffice.mail.entities.MailMessageQueueEvent
import traveloffice.mail.exceptions.MalformedMessageEventData
import traveloffice.mail.exceptions.UnSupportedEvent
import traveloffice.mail.services.MailMessageQueueService
import traveloffice.mail.services.MailService
import traveloffice.mail.services.SecretManagerService
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.stream.IntStream
import kotlin.streams.toList


@SpringBootApplication
class MailApplication : ApplicationRunner {
    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var messageQueueService: MailMessageQueueService

    @Autowired
    lateinit var mailService: MailService

    var logger: Logger = LoggerFactory.getLogger(MailApplication::class.java)

    companion object {
        var isMailQueueTask = false
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer? {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**").allowedOrigins("*")
            }
        }
    }

    override fun run(arg0: ApplicationArguments?) {
        val isMailQueueTask = secretManagerService.getEnv("MAIL_SERVICE_QUEUE_NAME").isNotEmpty()
        if (isMailQueueTask) {
            val maxMessagePerTask = secretManagerService.getEnv("MAIL_MAX_MESSAGE_PER_SERVICE_TASK").toInt()
            runBlocking {
                repeat(maxMessagePerTask) {
                    launch(Dispatchers.IO) {
                        pollMessageQueueEvent()
                    }
                }
            }
        }
    }

    private suspend fun pollMessageQueueEvent() {
        while (true) {
            try {
                val messages = this.messageQueueService.receiveMessage(maxNumberOfMessages = 1)
                if (!messages.isNullOrEmpty()) {
                    logger.info(
                        "Mail Queue Service Task: Receive message from message queue: ${
                            Gson().toJson(
                                messages
                            )
                        }"
                    )
                    this.handleMailMessageEvent(messages.first())
                }
            } catch (e: Exception) {
                logger.error("Mail Queue Service Task: Receive error: ", e)
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
    private suspend fun handleMailMessageEvent(event: MailMessageQueueEvent) {
        try {
            val messageData = event.data
                ?: throw MalformedMessageEventData("Handler Mail Message: Malformed message event data $event")
            when (messageData.action) {
                MailMessageQueueAction.syncMessage.toString() -> {
                    mailService.syncEmail(messageData)
                }

                else -> {
                    throw UnSupportedEvent("Handler Mail Message: Unsupported action event ${messageData.action}")
                }
            }
        } catch (e: Exception) {
            logger.error("Handler Mail Message: Receive error: ", e)
        } finally {
            /**
             * Delete the message after received to prevent the deviation in Mail Queue Service auto-scaling
             */
            this.messageQueueService.deleteMessage(event.receiptHandle).also {
                logger.info("Handler Mail Message: Deleted message from message queue: ${Gson().toJson(event)}")
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<MailApplication>(*args)
}
