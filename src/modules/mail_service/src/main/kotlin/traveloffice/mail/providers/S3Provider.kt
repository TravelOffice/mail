package traveloffice.mail.providers

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
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
     * This method perform to uploads a new object to the specified Amazon S3 bucket
     *
     * @param bucketName – The request object containing all the parameters to upload a new object to Amazon S3.
     * @param key – The request object containing all the parameters to upload a new object to Amazon S3.
     * @param content – The request object containing all the parameters to upload a new object to Amazon S3.
     *
     * @return A PutObjectResult object containing the information returned by Amazon S3 for the newly created object.
     * @exception
     */
    fun putObjectString(bucketName: String, key: String, content: String): PutObjectResult = this.s3Client.putObject(bucketName, key, content)

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
     * Copy object [sourceKey] of [bucket] to [destinationKey] of [bucket] and set [mimeType], [encoding] for new object
     */
    fun copyObject(bucket: String, sourceKey: String, destinationKey: String, mimeType: String, encoding: String): CopyObjectResult {
        val metadata = ObjectMetadata()
        metadata.contentType = mimeType
        metadata.contentEncoding = encoding
        val copyObjectRequest = CopyObjectRequest()
            .withSourceBucketName(bucket)
            .withSourceKey(sourceKey)
            .withDestinationBucketName(bucket)
            .withDestinationKey(destinationKey)
            .withNewObjectMetadata(metadata)
        return this.s3Client.copyObject(copyObjectRequest);
    };

    /**
     * Delete object with information in [deleteObjectRequest]
     */
    private fun deleteObject(deleteObjectRequest: DeleteObjectRequest) = this.s3Client.deleteObject(deleteObjectRequest)

    /**
     * Delete all objects version with information in [deleteObjectsRequest]
     */
    fun deleteObjects(deleteObjectsRequest: DeleteObjectsRequest): DeleteObjectsResult {
        val keyVersions = mutableListOf<KeyVersion>()
        deleteObjectsRequest.keys.map {
            val listVersionRequest = ListVersionsRequest()
                .withBucketName(deleteObjectsRequest.bucketName)
                .withPrefix(it.key)
            val response = s3Client.listVersions(listVersionRequest)
            keyVersions.addAll(response.versionSummaries.map { version ->
                return@map KeyVersion(version.key, version.versionId)
            })
        }
        if (keyVersions.isNotEmpty()) {
            // If object has version, remove all versions
            deleteObjectsRequest.keys = keyVersions
        }
        return this.s3Client.deleteObjects(deleteObjectsRequest)
    }

    /**
     * Move object [sourceKey] of [bucket] to [destinationKey] of [bucket] and set [mimeType], [encoding] for new object
     */
    fun moveObject(bucket: String, sourceKey: String, destinationKey: String, mimeType: String, encoding: String) {
        this.copyObject(bucket, sourceKey, destinationKey, mimeType, encoding);
        val deleteObjectRequest = DeleteObjectRequest(bucket, sourceKey)
        this.deleteObject(deleteObjectRequest)
    }

    /**
     * Get size of [objectKey] in [bucket]
     */
    fun getObjectSize(bucket: String, objectKey: String): Long {
        return this.s3Client.getObjectMetadata(bucket, objectKey).contentLength
    }
}
