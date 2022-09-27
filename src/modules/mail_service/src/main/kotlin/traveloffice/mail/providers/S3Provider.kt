package traveloffice.mail.providers

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.*
import traveloffice.mail.common.Constant
import java.net.URL

class S3Provider(region: String) {
    private var s3Client: AmazonS3

    init {
        val builder = AmazonS3ClientBuilder.standard().also {
            it.region = region
        }
        this.s3Client = builder.build()
    }

    /**
     * This method perform to uploads a new object to the specified Amazon S3 bucket
     *
     * @param putObjectRequest – The request object containing all the parameters to upload a new object to Amazon S3.
     *
     * @return A PutObjectResult object containing the information returned by Amazon S3 for the newly created object.
     * @exception
     */
    fun putObject(putObjectRequest: PutObjectRequest): PutObjectResult = this.s3Client.putObject(putObjectRequest)

    /**
     * This method perform to get an object from the specified Amazon S3 bucket
     *
     * @param getObjectRequest – The request object containing all the options on how to download the object.
     *
     * @return The object stored in Amazon S3 in the specified bucket and key. Returns null if constraints were specified but not met.
     * @exception
     */
    fun getObject(getObjectRequest: GetObjectRequest): S3Object? = this.s3Client.getObject(getObjectRequest)

    /**
     * Generate Pre-signed url for [generatePresignedUrl] and return [URL] object
     */
    fun generatePresignedUrl(generatePresignedUrlRequest: GeneratePresignedUrlRequest): URL = this.s3Client.generatePresignedUrl(generatePresignedUrlRequest)

    /**
     * Copy object [copyObjectRequest] then return [CopyObjectResult] object
     */
    fun copyObject(copyObjectRequest: CopyObjectRequest): CopyObjectResult = this.s3Client.copyObject(copyObjectRequest)

    /**
     * Delete object with information in [deleteObjectRequest]
     */
    fun deleteObject(deleteObjectRequest: DeleteObjectRequest) = this.s3Client.deleteObject(deleteObjectRequest)

    /**
     * Move object [sourceKey] of [sourceBucket] to [destinationKey] of [destinationBucket] and set [mimeType], [encoding] for new object
     */
    fun moveObject(bucket: String, sourceKey: String, destinationKey: String, mimeType: String, encoding: String) {
        val metadata = ObjectMetadata()
        metadata.contentType = mimeType
        metadata.contentEncoding = encoding
        val copyObjectRequest = CopyObjectRequest()
            .withSourceBucketName(bucket)
            .withSourceKey(sourceKey)
            .withDestinationBucketName(bucket)
            .withDestinationKey(destinationKey)
            .withNewObjectMetadata(metadata)
        this.copyObject(copyObjectRequest)
        val deleteObjectRequest = DeleteObjectRequest(bucket, sourceKey)
//        this.deleteObject(deleteObjectRequest)
    }

    /**
     * Get size of [objectKey] in [bucket]
     */
    fun getObjectSize(bucket: String, objectKey: String): Long {
        return this.s3Client.getObjectMetadata(bucket, objectKey).contentLength
    }
}
