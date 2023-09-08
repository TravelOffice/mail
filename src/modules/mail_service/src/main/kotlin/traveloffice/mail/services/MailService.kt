package traveloffice.mail.services

import com.amazonaws.services.kms.model.NotFoundException
import com.google.common.net.InternetDomainName
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.DependsOn
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.xbill.DNS.Lookup
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Type
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_DELETE
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_DRAFT
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_INBOX
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_SENT
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAILBOX_SPAM
import traveloffice.mail.common.Constant.Companion.DEFAULT_MAX_THREAD_POOL
import traveloffice.mail.common.Constant.Companion.MAIL_CDN
import traveloffice.mail.common.GracefulAction
import traveloffice.mail.common.SyncMessageLock
import traveloffice.mail.entities.*
import traveloffice.mail.entities.apis.AutoLinkEmailRequest
import traveloffice.mail.entities.apis.ChangeMailboxRequestBody
import traveloffice.mail.entities.apis.DeleteMailsRequestBody
import traveloffice.mail.entities.oauth2.EmailServerRecord
import traveloffice.mail.entities.oauth2.OAuth2User
import traveloffice.mail.exceptions.InvalidArguments
import traveloffice.mail.exceptions.MailBoxIsStillSyncing
import traveloffice.mail.exceptions.MailBoxNotExisted
import traveloffice.mail.repositories.MailRepository
import traveloffice.mail.repositories.SearchRepository
import java.util.*
import java.util.stream.Collectors

@Service
@DependsOn("DynamoDBMapper")
class MailService {
    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var mailRepository: MailRepository

    @Autowired
    lateinit var searchRepository: SearchRepository

    var logger: Logger = LoggerFactory.getLogger(MailRepository::class.java)

    @Value("classpath:isp.records.predefined.json")
    lateinit var ispResource: Resource

    /**
     * [authenticatedUser] user send email with [sendMailRequestPayload]
     * Return [MailMessage] object
     */
    fun sendEmail(authenticatedUser: AuthenticatedUser, sendMailRequestPayload: SendMailRequestPayload): MailMessage {
        val storeMessage = this.parseMailRequestPayload(sendMailRequestPayload)
        val sendingMessage = storeMessage.clone()
        sendingMessage.rawHtmlContent = storeMessage.rawHtmlContent

        // Check mail account exist or not
        val emailAddress = sendingMessage.fromAsInternetAddress.address
        val (tenant, username) = authenticatedUser
        val mailAccount: MailAccount = this.mailRepository.getMailAccount(
            tenant,
            username,
            emailAddress
        )

        // Download attachments from s3 bucket
        if (sendingMessage.attachments != null) {
            sendingMessage.attachments?.forEach {
                it.inputStream = this.mailRepository.getObjectFromS3(it.path)
            }
        }

        // Send email
        this.mailRepository.sendEmail(mailAccount, sendingMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Send email successfully")

        storeMessage.sentDate = Date().time

        // Check mail is DRAFT or not
        if (sendMailRequestPayload.messageId === null) {
            // Copy Html content and attachments to mail folder on s3
            this.mailRepository.storeEmailToS3(tenant, username, storeMessage)
            logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store sent message to s3 successfully")

            // Store email to dynamo db
            this.mailRepository.saveEmail(mailAccount, DEFAULT_MAILBOX_SENT, storeMessage)
            logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store sent email to database successfully")
        } else {
            this.mailRepository.changeMailboxOfMessage(tenant, username, DEFAULT_MAILBOX_DRAFT, DEFAULT_MAILBOX_SENT, emailAddress, storeMessage.messageId, storeMessage.sentDate)
            logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Change email from DRAFT to SENT successfully")
        }

        return storeMessage
    }

    /**
     * Parse [requestPayload] to [MailMessage]
     * Return [MailMessage] object
     */
    private fun parseMailRequestPayload(requestPayload: SendMailRequestPayload): MailMessage {
        val storeMessage = MailMessage()
        storeMessage.messageId = if(requestPayload.messageId !== null) requestPayload.messageId else UUID.randomUUID().toString()
        storeMessage.subject = requestPayload.subject
        storeMessage.htmlContent = requestPayload.htmlContent
        storeMessage.from = requestPayload.from
        storeMessage.attachments = requestPayload.attachments?.map {
            MailAttachment.Builder()
                .withName(it.name)
                .withEncoding(it.encoding)
                .withMimeType(it.mimeType)
                .withPath(it.path)
                .build()
        }
        storeMessage.to = BriefAddress.Builder().withAddresses(requestPayload.to).build()
        storeMessage.cc = requestPayload.cc?.let { BriefAddress.Builder().withAddresses(it).build() }
        storeMessage.bcc = requestPayload.bcc?.let { BriefAddress.Builder().withAddresses(it).build() }
        // Download Html content from S3 Pre-signed url
        storeMessage.rawHtmlContent = MailAttachment.Builder()
            .withName("index.html")
            .withMimeType("text/html; charset=utf-8")
            .withInputStream(this.mailRepository.getObjectFromS3(storeMessage.htmlContent))
            .build()
        return storeMessage
    }

    /**
     * [authenticatedUser] user draft email with [draftMailRequestPayload]
     * Return [MailMessage] object
     */
    fun saveDraft(authenticatedUser: AuthenticatedUser, draftMailRequestPayload: SendMailRequestPayload): MailMessage {
        val storeMessage = this.parseMailRequestPayload(draftMailRequestPayload)
        // Check mail account exist or not
        val emailAddress = storeMessage.fromAsInternetAddress.address
        val (tenant, username) = authenticatedUser
        val mailAccount: MailAccount = this.mailRepository.getMailAccount(
            tenant,
            username,
            emailAddress
        )

        // Copy Html content and attachments to mail folder on s3
        this.mailRepository.storeEmailToS3(tenant, username, storeMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store draft message to s3 successfully")

        // Dummy sentDate for DRAFT email to support search engine
        storeMessage.sentDate = Date().time

        // Store draft email to dynamo db
        this.mailRepository.saveEmail(mailAccount, DEFAULT_MAILBOX_DRAFT, storeMessage)
        logger.info("Tenant [${tenant}], User [${username}], Mail ID [${storeMessage.messageId}], Store draft email to database successfully")

        return storeMessage
    }

    /**
     * This method perform sync message from provider, currently only supported the sync message from
     * the mailbox INBOX
     *
     * The method perform following steps:
     * Step 1: Get the current mail account information
     * Step 2: Get the target MailBox to sync
     * Step 3: Mark the sync status to SYNCING to prevent duplicate sync
     * Step 4: Try to sync new email from the last sync time
     * Step 5: Save the new synced messages to DynamoDB and S3
     * Step 6: Mark the sync status to SYNCED
     * Step 7: Save the last sync time to the database
     * Step 8: Update the mailbox size
     * Step 9: If sync error send Sync Email failed notification, and mark the sync status to SYNC_ERR
     *
     * @param data: MailMessageQueueData the event data which received from the Mail Queue
     * @return
     * @exception MailBoxIsStillSyncing Raise exception when MailBox currently is syncing the message
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Throws(MailBoxIsStillSyncing::class, Exception::class)
    suspend fun syncEmail(data: MailMessageQueueData) {
        val (tenant, username, _, arguments) = data
        val box = arguments["mailbox"]?.toString()?.uppercase()
        val account = arguments["email"]?.toString()
        if (box.isNullOrEmpty() || account.isNullOrEmpty())
            throw InvalidArguments("Missing mailbox or mail account")

        // Get the mail account information
        val mailAccount = this.mailRepository.getMailAccount(tenant, username, account)

        // Get the mailbox information
        var mailBox: MailBox
        SyncMessageLock.mutex.withLock {
            mailBox = this.mailRepository.getMailBox(mailAccount, box)
                ?: throw NotFoundException("MailBox [$box] not found!, tenant [$tenant] username [$username] account [$account]")
            // Return intermediately when current MailBox is still syncing
            if (mailBox.syncStatus == MailBoxSyncStatus.SYNCING.toString()) {
                throw MailBoxIsStillSyncing("MailBox [${mailBox.name}] is still syncing!")
            }
            // Mark the sync status to SYNCING to prevent duplicate sync
            this.mailRepository.changeMailBoxStatus(mailBox, MailBoxSyncStatus.SYNCING)
        }

        var mailBoxSyncStatus = MailBoxSyncStatus.SYNCING
        var firstSync = false
        try {
            val lastSyncInfo = this.mailRepository.getSyncInfo(mailAccount, mailBox.name, false)
            if (lastSyncInfo.isEmpty()) firstSync = true
            else {
                // Replicate email to search engine to prevent missing search items
                this.mailRepository.replicateEmailToSearchEngine(mailAccount)
            }
            // Try to fetch new message from the provider
            val messages = this.mailRepository.getNewEmailFromLastSync(mailAccount, mailBox, lastSyncInfo)
            if (messages.isEmpty()) {
                logger.info(
                    "${mailAccount.inboundType.uppercase()} [${mailBox.name}] Not found any new message " +
                            "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                            "Mail Account [${mailAccount.email}] "
                )
                // Mark the sync status to SYNC successfully
                mailBoxSyncStatus = MailBoxSyncStatus.SYNCED
                return
            }

            logger.info(
                "${mailAccount.inboundType.uppercase()} [${mailBox.name}] found ${messages.size} new message " +
                        "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                        "Mail Account [${mailAccount.email}] "
            )
            val parsedResult = Collections.synchronizedList(mutableListOf<MailMessageSyncStatus>())

            runBlocking {
                messages.map { message ->
                    launch(Dispatchers.IO.limitedParallelism(DEFAULT_MAX_THREAD_POOL)) {
                        val syncStatus = MailMessageSyncStatus()
                        syncStatus.uid = message.uid!!
                        syncStatus.messageNumber = message.raw!!.messageNumber
                        try {
                            if (GracefulAction.interrupted)
                                throw Exception("Interrupt received! Exiting parse mail message immediately")
                            mailRepository.parseMailMessage(mailAccount, message)
                            // Save the emails information to database
                            mailRepository.saveEmail(mailAccount, mailBox.name, message)
                            syncStatus.size = message.size
                            syncStatus.syncTime = message.sentDate
                            if (!firstSync)
                                syncStatus.message = message
                        } catch (e: Exception) {
                            syncStatus.failed = true
                            syncStatus.errorMessage = e.message
                            logger.info(
                                "Parse failed [${syncStatus.messageNumber}][${message.uid}] " +
                                        "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                                        "Mail Account [${mailAccount.email}] " +
                                        "with error: ${e.message}"
                            )
                            e.printStackTrace()
                        } finally {
                            parsedResult.add(syncStatus)
                        }
                    }
                }
            }

            // Sort the result to get the highest message number first
            parsedResult.sortByDescending { it.messageNumber }
            val parsedSuccessResult = parsedResult.filter { !it.failed }
            val parsedFailedResult: List<MailMessageSyncStatus>
            val mailMessages = if (firstSync) {
                // For the first sync only send to client the number of message synced
                emptyList()
            } else {
                parsedSuccessResult.map { it.message!! }
            }

            if (parsedSuccessResult.isNotEmpty()) {
                logger.info(
                    "${mailAccount.inboundType.uppercase()} [${mailBox.name}] parse successfully ${parsedSuccessResult.size} message " +
                            "from Tenant [${mailAccount.tenant}] User [${mailAccount.username}] " +
                            "Mail Account [${mailAccount.email}] "
                )
                val lastSuccessMessageNumber = parsedSuccessResult.first().messageNumber
                parsedFailedResult = parsedResult.filter { it.failed && it.messageNumber < lastSuccessMessageNumber }

                // Send the new email notification to AppSync
                mailRepository.notifyNewEmailsReceived(
                    mailAccount,
                    mailBox,
                    firstSync,
                    parsedSuccessResult.size,
                    mailMessages
                )
            } else {
                parsedFailedResult = parsedResult.filter { it.failed }
            }
            // Save the sync message information to the database
            mailRepository.saveSyncInfo(
                mailAccount,
                mailBox.name,
                parsedSuccessResult,
                parsedFailedResult,
                lastSyncInfo
            )
            // Mark the sync status to SYNC successfully
            mailBoxSyncStatus = MailBoxSyncStatus.SYNCED
        } catch (e: Exception) {
            logger.error("Sync Email failed: message ${e.message}")
            // Mark the sync status to SYNC ERROR
            mailBoxSyncStatus = MailBoxSyncStatus.SYNC_ERR
            throw e
        } finally {
            // Change status of mailbox
            this.mailRepository.changeMailBoxStatus(mailBox, mailBoxSyncStatus)
        }
    }


    /**
     * This method use to link email to the TO systems
     *
     * @param mailAccount The mail account information
     * @param authenticatedUser The user is decoded from cognito token
     * @return void
     * @exception
     */
    fun linkEmail(mailAccount: MailAccount, authenticatedUser: AuthenticatedUser) = try {
        val (tenant, username) = authenticatedUser
        // Validate mail account information by this step
        mailRepository.validateMailAccount(mailAccount, authenticatedUser)
        // Encrypt password
        val encryptionKey = this.secretManagerService.getAWSSecretValue(Constant.ENCRYPT_SECRET_KEY)
        // Save mail account information to dynamodb
        mailRepository.linkEmail(mailAccount, authenticatedUser, encryptionKey)
        searchRepository.invalidateApiKey(tenant, username)
        logger.info("Link mail account ${mailAccount.email} successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }

    fun autolinkEmail(autoLinkEmailRequest: AutoLinkEmailRequest, authenticatedUser: AuthenticatedUser) = try {
        val (tenant, username) = authenticatedUser
        var isOAuth = false
        var user: OAuth2User? = null

        // Validate authentication params
        if (autoLinkEmailRequest.password == null) {
            if (autoLinkEmailRequest.code == null || autoLinkEmailRequest.state == null)
                throw Exception("Missing authentication params")
            user = mailRepository.getOAuthUserInfo(tenant, username, autoLinkEmailRequest)
            isOAuth = true
        }

        // Build mail account information
        val mailAccount = MailAccount()
        if (isOAuth) {
            mailAccount.email = user!!.email
            mailAccount.name = user.name
            mailAccount.oauth2Token = user.oAuth2Token
        } else {
            mailAccount.email = autoLinkEmailRequest.email
            mailAccount.name = autoLinkEmailRequest.name
            mailAccount.inboundPassword = autoLinkEmailRequest.password
            mailAccount.outboundPassword = autoLinkEmailRequest.password
        }

        val emailServerInfo = getEmailServerInformation(authenticatedUser, mailAccount.email)
            ?: throw Exception("Unsupported auth type when manual link email")
        val (name, provider, _, incoming, outgoing) = emailServerInfo

        mailAccount.protocol = incoming.type
        mailAccount.provider = provider
        mailAccount.providerName = name
        mailAccount.tenant = tenant
        mailAccount.username = username

        // Inbound information
        mailAccount.inboundHost = incoming.host
        mailAccount.inboundAuthType = incoming.authentication_method
        mailAccount.inboundSslMode = incoming.connection_security
        mailAccount.inboundPort = incoming.port
        if (incoming.username_login == true) {
            mailAccount.inboundUser = mailAccount.email.substringBefore("@")
        } else {
            mailAccount.inboundUser = mailAccount.email
        }

        // Outbound information
        mailAccount.outboundHost = outgoing.host
        mailAccount.outboundAuthType = outgoing.authentication_method
        mailAccount.outboundSslMode = outgoing.connection_security
        mailAccount.outboundPort = outgoing.port
        if (outgoing.username_login == true) {
            mailAccount.outboundUser = mailAccount.email.substringBefore("@")
        } else {
            mailAccount.outboundUser = mailAccount.email
        }

        // Link email to the TO systems
        this.linkEmail(mailAccount, authenticatedUser)
        logger.info("Link mail account successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }

    /**
     * UnLink mail account for given [mail] which related to [authenticatedUser]
     * The user [authenticatedUser] contains **tenant** and **username** values
     *
     */
    fun unlinkEmail(mail: String, authenticatedUser: AuthenticatedUser) = try {
        val (tenant, username) = authenticatedUser
        // Check if mail account exists before unlink
        val mailAccount = this.mailRepository.getMailAccount(tenant, username, mail)
        // Unlink email and also remove all mail information
        mailRepository.unlinkEmail(tenant, username, mailAccount)
        searchRepository.invalidateApiKey(tenant, username)
        logger.info("Unlink mail account $mail successfully")
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }


    /**
     * Get mail boxes for given [mail] which related to [authenticatedUser]
     * The user [authenticatedUser] contains **tenant** and **username** values
     *
     * Returns list of mail boxes information with exposed data
     */
    fun getMailBoxes(mail: String, authenticatedUser: AuthenticatedUser): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            return mailRepository
                .getMailBoxes(tenant, username, mail)
                .sortedWith(
                    // Sort by following order INBOX > SENT > DRAFT > SPAM > DELETE > OTHERS
                    compareBy(
                        { it.name == DEFAULT_MAILBOX_DELETE },
                        { it.name == DEFAULT_MAILBOX_SPAM },
                        { it.name == DEFAULT_MAILBOX_DRAFT },
                        { it.name == DEFAULT_MAILBOX_SENT },
                        { it.name == DEFAULT_MAILBOX_INBOX }
                    )
                )
                .map { it.toJson() }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get mail accounts for [authenticatedUser]
     *
     * Returns list of mail accounts information with exposed data
     */
    fun getUserMailAccounts(authenticatedUser: AuthenticatedUser): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            return mailRepository
                .getUserMailAccounts(tenant, username)
                .map { it.toJson() }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get mail accounts storage for [authenticatedUser]
     *
     * Returns mail accounts storage information
     */
    fun getUserMailStorage(authenticatedUser: AuthenticatedUser): Map<String, Any> {
        try {
            val (tenant, username) = authenticatedUser
            val totalStorage = mailRepository.getUserMailStorage(tenant, username)
            return mapOf(
                "totalStorage" to totalStorage
            )
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get mail server information from [mail] for [authenticatedUser]
     *
     * Returns mail accounts storage information
     */
    fun getEmailServerInformation(authenticatedUser: AuthenticatedUser, mail: String): EmailServerRecord? {
        try {
            val emailServerRecords =
                Gson().fromJson(String(ispResource.inputStream.readAllBytes()), Array<EmailServerRecord>::class.java).toList()
            val domain = mail.substringAfter("@")
            // If domain is outlook.com
            if (domain == "outlook.com") {
                return emailServerRecords.find { it.provider == "outlook" }
            }
            // Find domain in email server records first
            var serverInfo = emailServerRecords.find { it.domains.contains(domain) }
            if (serverInfo == null) {
                // query the MX record to find isp domain name
                val lookup = Lookup(domain, Type.MX)
                val mxRecords = lookup.run()
                if (mxRecords.isNullOrEmpty()) {
                    return null
                }
                val targets = mxRecords.map {
                    val mxDomain = (it as MXRecord).target.canonicalize().toString(true)
                    InternetDomainName.from(mxDomain).topPrivateDomain().toString()
                }
                val targetsWithoutDuplicate = targets.stream().distinct().collect(Collectors.toList())
                serverInfo = emailServerRecords.find {
                    for (target in targetsWithoutDuplicate) {
                        if (it.domains.contains(target))
                            return@find true
                    }
                    return@find false
                }
                if (serverInfo == null) return null
            }

            if (serverInfo.incoming.authentication_method == "oauth2"
                || serverInfo.outgoing.authentication_method == "oauth2"
            ) {
                // Generate the oauth url
                val (tenant, username) = authenticatedUser
                serverInfo.oauthUrl = mailRepository.getOAuth2Url(tenant, username, serverInfo.provider, mail)
            }
            return serverInfo
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Generate presigned url to put [fileName] with [contentType] to s3 bucket
     * Return [PresignedUrlResponsePayload] object
     */
    fun generatePresignedUrl(fileName: String, contentType: String): PresignedUrlResponsePayload = try {
        mailRepository.generatePresignedUrl(fileName, contentType)
    } catch (e: Exception) {
        logger.error(e.toString())
        throw e
    }

    /**
     * Get mail boxes for given [getMailParams] which related to [authenticatedUser]
     * The user [authenticatedUser] contains **tenant** and **username** values
     *
     * Returns list of thread mail messages information with exposed data
     */
    fun getMailMessagesFromMailBox(
        getMailParams: GetMailRequestParams,
        authenticatedUser: AuthenticatedUser
    ): List<Map<String, Any>> {
        try {
            val (tenant, username) = authenticatedUser
            val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
            return mailRepository.getThreadMails(tenant, username, getMailParams)
                .map { it.toJson(mailCDN) }
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get [mailbox] mails of [mail] address for given [authenticatedUser] returned from Cognito
     *
     * Returns [List] of [Map] with exposed data
     */
    fun getMailsInsideMailBox(
        authenticatedUser: AuthenticatedUser,
        mail: String,
        mailbox: String,
        token: String?
    ): Map<String, Any> {
        try {
            val (tenant, username) = authenticatedUser
            val (lastEvaluatedKey, emails) = mailRepository.getMailsInsideMailBox(
                tenant,
                username,
                mail,
                mailbox,
                token
            )

            val mailCDN = this.secretManagerService.getAWSSecretValue(MAIL_CDN)
            return mapOf(
                "token" to lastEvaluatedKey,
                "mails" to emails.map { it.toJson(mailCDN) }
            )
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Change mailbox for given email with data [changeMailboxData] of user [authenticatedUser]
     * Two types will be supported:
     * - Change mailbox for individual message
     * - Change mailbox for list of messages
     */
    @Throws(MailBoxNotExisted::class, Exception::class)
    fun changeMailbox(authenticatedUser: AuthenticatedUser, changeMailboxData: ChangeMailboxRequestBody) {
        try {
            // Extract data
            val (tenant, username) = authenticatedUser
            val (from, to, mail, messages) = changeMailboxData

            // Get mailboxes that belongs to user's mail
            val mailboxes = mailRepository.getMailBoxes(tenant, username, mail)
            val strMailBoxes = mailboxes.map { it.name.uppercase() }

            // Check from, to mailboxes existed
            if (!strMailBoxes.contains(from.uppercase()) || !strMailBoxes.contains(to.uppercase())) {
                throw MailBoxNotExisted("Mailbox parameter $from or $to is not existed")
            }

            // Start moving between mailboxes
            if (messages != null) {
                if (messages.size == 1) {
                    // Change mailbox for individual message
                    val message = messages.first()
                    mailRepository.changeMailboxOfMessage(tenant, username, from, to, mail, message)
                } else {
                    // Batch change mailbox for list of messages
                    mailRepository.changeMailboxOfMessages(tenant, username, from, to, mail, messages)
                }

            }

        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Delete mails form mailbox for given email with data [deleteMailsRequest] of user [authenticatedUser]
     */
    @Throws(MailBoxNotExisted::class, Exception::class)
    fun deleteMailsFromMailBox(authenticatedUser: AuthenticatedUser, deleteMailsRequest: DeleteMailsRequestBody) {
        try {
            // Extract tenant data
            val (tenant, username) = authenticatedUser
            mailRepository.deleteMailMessages(
                tenant,
                username,
                deleteMailsRequest.email,
                deleteMailsRequest.mailbox,
                deleteMailsRequest.messagesId
            )
        } catch (e: Exception) {
            logger.error(e.toString())
            throw e
        }
    }

    /**
     * Get the mail search token for specific tenant user [authenticatedUser]
     *
     * Returns [String] of search token
     */
    fun getSearchToken(authenticatedUser: AuthenticatedUser): String? {
        return try {
            // Extract tenant data
            val (tenant, username) = authenticatedUser
            val apiKey = searchRepository.generateApiKeys(tenant, username)
                ?: throw Exception("Cannot get search api key for tenant [$tenant] user [$username]")
            searchRepository.generateSearchToken(apiKey)
        } catch (e: Exception) {
            logger.error(e.toString())
            ""
        }
    }
}
