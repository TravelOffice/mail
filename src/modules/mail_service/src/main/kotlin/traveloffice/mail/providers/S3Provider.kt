package traveloffice.mail.providers

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.PutObjectResult
import com.amazonaws.services.s3.model.S3Object
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
     * This method performs to generate S3 presigned url
     *
     * @param generatePresignedUrlRequest The request object containing all the options on how to generate a presigned url
     * @return The URL Object
     */
    fun generatePresignedUrl(generatePresignedUrlRequest: GeneratePresignedUrlRequest): URL = this.s3Client.generatePresignedUrl(generatePresignedUrlRequest)
}
