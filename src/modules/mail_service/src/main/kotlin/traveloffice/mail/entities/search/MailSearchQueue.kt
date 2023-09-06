package traveloffice.mail.entities.search

import com.meilisearch.sdk.model.Settings

class MailSearchQueue {
    lateinit var type: String

    lateinit var operation: String

    lateinit var indexName: String

    var primaryKey: String? = null

    var indexSettings: Settings? = null

    var documentData: Any? = null

    var documentId: String? = null

    class Builder {
        private var mailSearchQueue = MailSearchQueue()

        fun withType(type: MailSearchQueueType): Builder {
            this.mailSearchQueue.type = type.toString()
            return this
        }

        fun withOperation(operation: MailSearchQueueOperation): Builder {
            this.mailSearchQueue.operation = operation.toString()
            return this
        }

        fun withIndexName(indexName: String): Builder {
            this.mailSearchQueue.indexName = indexName
            return this
        }

        fun withPrimaryKey(primaryKey: String): Builder {
            if (this.mailSearchQueue.type == MailSearchQueueType.INDEX.toString()) {
                this.mailSearchQueue.primaryKey = primaryKey
            }
            return this
        }

        fun withIndexSettings(indexSettings: Settings): Builder {
            if (this.mailSearchQueue.type == MailSearchQueueType.INDEX.toString()) {
                this.mailSearchQueue.indexSettings = indexSettings
            }
            return this
        }

        fun withDocumentData(documentData: Any): Builder {
            if (this.mailSearchQueue.type == MailSearchQueueType.DOCUMENT.toString()
                    && this.mailSearchQueue.operation != MailSearchQueueOperation.DELETE.toString()) {
                this.mailSearchQueue.documentData = documentData
            }
            return this
        }

        fun withDocumentId(documentId: String): Builder {
            if (this.mailSearchQueue.type == MailSearchQueueType.DOCUMENT.toString()
                    && this.mailSearchQueue.operation == MailSearchQueueOperation.DELETE.toString()) {
                this.mailSearchQueue.documentId = documentId
            }
            return this
        }

        fun build(): MailSearchQueue {
            return this.mailSearchQueue
        }
    }
}
