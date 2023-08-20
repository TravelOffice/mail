package traveloffice.mail.services

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAX_NUMBER_MESSAGE
import traveloffice.mail.common.Constant.Companion.DEFAULT_WAIT_TIME_SECOND
import traveloffice.mail.entities.MailMessageQueueData
import traveloffice.mail.entities.MailMessageQueueEvent
import traveloffice.mail.repositories.MessageQueueRepository

@Service
@DependsOn("SecretManager")
class MailMessageQueueService {
    @Autowired
    lateinit var messageQueueRepository: MessageQueueRepository

    @Autowired
    lateinit var secretManagerService: SecretManagerService
    var logger: Logger = LoggerFactory.getLogger(MailMessageQueueService::class.java)

    /**
     * This method receive message from the Amazon SQS
     *
     * @return MailMessageQueueEvent Result of the ReceiveMessage operation returned by the service.
     * @exception
     */
    fun receiveMessage(
        waitTimeSeconds: Int? = DEFAULT_WAIT_TIME_SECOND,
        maxNumberOfMessages: Int? = DEFAULT_MAX_NUMBER_MESSAGE
    ): List<MailMessageQueueEvent>? {
        val queueUrl = this.secretManagerService.getEnv("MAIL_SERVICE_QUEUE_URL")
        val messagesData = this.messageQueueRepository.receiveMessage(queueUrl, waitTimeSeconds, maxNumberOfMessages)
        return messagesData?.messages()?.map {
            var data: MailMessageQueueData? = null
            try {
                data = Gson().fromJson(it.body(), MailMessageQueueData::class.java)
            } catch (e: JsonSyntaxException) {
                logger.error("Malformed message event data ${it.body()}")
            }
            return@map MailMessageQueueEvent(
                messageId = it.messageId(),
                receiptHandle = it.receiptHandle(),
                data = data,
            )
        }
    }

    /**
     * This method delete message from the Mail Queue
     *
     * @param receiptHandle: The receipt handle associated with the message to delete.
     * @return Result of the DeleteMessage operation returned by the service.
     * @exception
     */
    fun deleteMessage(receiptHandle: String) {
        val queueUrl = this.secretManagerService.getEnv("MAIL_SERVICE_QUEUE_URL")
        return this.messageQueueRepository.deleteMessage(queueUrl, receiptHandle)
    }
}
