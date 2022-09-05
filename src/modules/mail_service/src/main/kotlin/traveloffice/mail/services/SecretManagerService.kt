package traveloffice.mail.services

import org.apache.tomcat.util.json.JSONParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.*
import java.security.InvalidParameterException
import java.util.*


@Component
class SecretManagerService {
    @Autowired
    private lateinit var environment: Environment

    @Value("\${AWS_REGION}")
    private val awsRegion: String? = null

    @Value("\${SECRET_MANAGER_ID}")
    private val awsSecretName: String? = null

    private lateinit var secretValues: Map<String, Any>

    fun getEnv(key: String): String {
        return environment[key] ?: ""
    }

    // Use this code snippet in your app.
    // If you need more information about configurations or implementing the sample code, visit the AWS docs:
    // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites

    // Use this code snippet in your app.
    // If you need more information about configurations or implementing the sample code, visit the AWS docs:
    // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites
    fun getAWSSecretValue(key: String): String {
        if (! ::secretValues.isInitialized) {
            val region: Region = Region.of(awsRegion)

            // Create a Secrets Manager client
            val client: SecretsManagerClient = SecretsManagerClient.builder().region(region).build()

            // In this sample we only handle the specific exceptions for the 'GetSecretValue' API.
            // See https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
            // We rethrow the exception by default.
            val getSecretValueRequest: GetSecretValueRequest = GetSecretValueRequest.builder().secretId(awsSecretName).build()
            val getSecretValueResponse: GetSecretValueResponse? = try {
                client.getSecretValue(getSecretValueRequest)
            } catch (e: DecryptionFailureException) {
                // Secrets Manager can't decrypt the protected secret text using the provided KMS key.
                // Deal with the exception here, and/or rethrow at your discretion.
                throw e
            } catch (e: InternalServiceErrorException) {
                // An error occurred on the server side.
                // Deal with the exception here, and/or rethrow at your discretion.
                throw e
            } catch (e: InvalidParameterException) {
                // You provided an invalid value for a parameter.
                // Deal with the exception here, and/or rethrow at your discretion.
                throw e
            } catch (e: InvalidRequestException) {
                // You provided a parameter value that is not valid for the current state of the resource.
                // Deal with the exception here, and/or rethrow at your discretion.
                throw e
            } catch (e: ResourceNotFoundException) {
                // We can't find the resource that you asked for.
                // Deal with the exception here, and/or rethrow at your discretion.
                throw e
            }

            // Decrypts secret using the associated KMS key.
            // Depending on whether the secret is a string or binary, one of these fields will be populated.
            val secretValueStr = if (getSecretValueResponse == null) {
                ""
            } else if (getSecretValueResponse.secretString() != null) {
                getSecretValueResponse.secretString()
            } else {
                String(Base64.getDecoder().decode(getSecretValueResponse.secretBinary().asByteBuffer()).array())
            }

            secretValues = JSONParser(secretValueStr).parseObject()
        }
        return (this.secretValues[key] ?: "") as String
    }
}