package traveloffice.mail.providers

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse

class SQSProvider(region: String) {
    private var sqsClient: SqsClient

    init {
        this.sqsClient = SqsClient.builder()
            .region(Region.of(region))
            .build()
    }

    /**
     * This method receive message from the Amazon SQS
     *
     * @param receiveRequest: The receive message requests.

     * @return Result of the ReceiveMessage operation returned by the service.
     * @exception
     */
    fun receiveMessage(receiveRequest: ReceiveMessageRequest): ReceiveMessageResponse? {
        return this.sqsClient.receiveMessage(receiveRequest)
    }

    /**
     * This method delete message from the Amazon SQS
     *
     * @param deleteMessageRequest: The delete message requests.

     * @return Result of the DeleteMessage operation returned by the service.
     * @exception
     */
    fun deleteMessage(deleteMessageRequest: DeleteMessageRequest): DeleteMessageResponse? {
        return this.sqsClient.deleteMessage(deleteMessageRequest)
    }
}