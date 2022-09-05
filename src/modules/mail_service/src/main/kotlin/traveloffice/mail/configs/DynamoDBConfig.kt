package traveloffice.mail.configs

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.services.SecretManagerService

@Configuration
class DynamoDBConfig {
    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Bean("DynamoDBMapper")
    @DependsOn("SecretManager")
    fun getDynamoDBMapper(): DynamoDBMapper {
        val builder = AmazonDynamoDBClientBuilder.standard().also {
            it.region = this.secretManagerService.getEnv("AWS_REGION")
        }
        val client: AmazonDynamoDB = builder.build()
        val env = this.secretManagerService.getEnv("ENV")
        val tableNamePrefix = "$env-"
        val mapperConfig = DynamoDBMapperConfig.Builder().withTableNameOverride(
            DynamoDBMapperConfig.TableNameOverride.withTableNamePrefix(tableNamePrefix)
        ).build()
        return DynamoDBMapper(client, mapperConfig)
    }
}