package traveloffice.mail.repositories

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SqsException
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAX_NUMBER_MESSAGE
import traveloffice.mail.common.Constant.Companion.DEFAULT_VISIBLE_TIME_OUT
import traveloffice.mail.common.Constant.Companion.DEFAULT_WAIT_TIME_SECOND
import traveloffice.mail.providers.SQSProvider


@Repository
@DependsOn(value = ["SecretManager"])
class MessageQueueRepository {
    @Autowired
    lateinit var sqsProvider: SQSProvider

    /**
     * This method receive message from the Amazon SQS
     *
     * @param queueUrl: The URL of the Amazon SQS queue from which messages are received.
     * @param waitTimeSeconds: The duration (in seconds) for which the call waits for a message
     *                         to arrive in the queue before returning. If a message is available, the call returns
     *                         sooner than WaitTimeSeconds. If no messages are available and the wait time expires,
     *                         the call returns successfully with an empty list of messages.
     * @param maxNumberOfMessages The maximum number of messages to return. Amazon SQS never
     *                            returns more messages than this value (however, fewer messages might be returned).
     *                            Valid values: 1 to 10. Default: DEFAULT_MAX_NUMBER_MESSAGE.
     *
     * @return Result of the ReceiveMessage operation returned by the service.
     * @exception
     */
    fun receiveMessage(
        queueUrl: String,
        waitTimeSeconds: Int? = DEFAULT_WAIT_TIME_SECOND,
        maxNumberOfMessages: Int? = DEFAULT_MAX_NUMBER_MESSAGE
    ): ReceiveMessageResponse? {
        try {
            val receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxNumberOfMessages)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(DEFAULT_VISIBLE_TIME_OUT)
                .build()
            return this.sqsProvider.receiveMessage(receiveMessageRequest)
        } catch (e: SqsException) {
            println(e.awsErrorDetails().errorMessage())
        }
        return null
    }

    /**
     * This method delete message from the Mail Queue
     *
     * @param queueUrl: The URL of the Amazon SQS queue from which messages are deleted.
     * @param receiptHandle: The receipt handle associated with the message to delete.
     *
     * @return Result of the DeleteMessage operation returned by the service.
     * @exception
     */
    fun deleteMessage(queueUrl: String, receiptHandle: String) {
        val deleteMessageRequest = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build()
        this.sqsProvider.deleteMessage(deleteMessageRequest)
    }
}