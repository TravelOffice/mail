import enum
import json
import boto3
import meilisearch

# Meili Search api-endpoint
URL = "http://localhost"
MASTER_KEY = "${MEILISEARCH_MASTERKEY}"

# Initialize the MeiliSearch client
meilisearch_client = meilisearch.Client(URL, MASTER_KEY)

# Initialize the SQS client using the IAM role attached to your EC2 instance
sqs = boto3.client('sqs', region_name='${REGION}')

# Specify the URL of the SQS queue you want to process messages from
queue_url = "${SEARCH_QUEUE}"

# Define an Enum for CRUD operations on indexes


class Operation(enum.Enum):
    CREATE = "CREATE"
    UPDATE = "UPDATE"
    DELETE = "DELETE"

# Function to process messages


def process_sqs_message(message):
    try:
        # Parse the message
        message_body = json.loads(message['Body'])
        message_type = message_body.get("type")

        if message_type == "INDEX":
            process_index_message(message_body)
        elif message_type == "DOCUMENT":
            process_document_message(message_body)
        else:
            print("Unknown message type:", message_type)
    except Exception as e:
        print("Error processing message:", str(e))

# Process messages related to indexes


def process_index_message(message):
    operation = message.get("operation")
    index_name = message.get("indexName")
    if operation == Operation.CREATE.value:
        primary_key = message.get("primaryKey")
        # Read index settings from the message
        index_settings = message.get("indexSettings")
        create_index(index_name, primary_key, index_settings)
    elif operation == Operation.UPDATE.value:
        # Read updated index settings from the message
        index_settings = message.get("indexSettings")
        update_index(index_name, index_settings)
    elif operation == Operation.DELETE.value:
        delete_index(index_name)
    else:
        print("Unknown index operation:", operation)

# Process messages related to documents


def process_document_message(message):
    operation = message.get("operation")
    index_name = message.get("indexName")
    if operation == Operation.CREATE.value:
        document_data = message.get("documentData")
        create_document(index_name, document_data)
    elif operation == Operation.UPDATE.value:
        document_id = message.get("documentId")
        document_data = message.get("documentData")
        update_document(index_name, document_id, document_data)
    elif operation == Operation.DELETE.value:
        document_id = message.get("documentId")
        delete_document(index_name, document_id)
    else:
        print("Unknown document operation:", operation)

# CRUD operations using MeiliSearch with error handling

# Modify the create_index function to accept primary_key and index_settings as a parameter


def create_index(index_name, primary_key=None, index_settings=None):
    try:
        # Create the index with the specified primary key
        meilisearch_client.create_index(uid=index_name, options={
                                        "primaryKey": primary_key})

        # If index_settings is provided, use it; otherwise, use an empty dictionary
        settings_to_apply = index_settings or {}

        # Update the index settings with the provided settings
        meilisearch_client.index(index_name).update_settings(settings_to_apply)

        print(f"Created index: {index_name}")
    except Exception as e:
        error_message = f"Error creating index {index_name}: {str(e)}"
        print(error_message)
        raise Exception(error_message)

# Modify the update_index function to accept index_settings as a parameter


def update_index(index_name, index_settings):
    try:
        # Update the index settings with the provided settings
        meilisearch_client.index(index_name).update_settings(index_settings)
        print(f"Updated index settings for index {index_name}")
    except Exception as e:
        error_message = f"Error updating index settings for index {index_name}: {str(e)}"
        print(error_message)
        raise Exception(error_message)


def delete_index(index_name):
    try:
        meilisearch_client.index(index_name).delete()
        print(f"Deleted index: {index_name}")
    except Exception as e:
        error_message = f"Error deleting index {index_name}: {str(e)}"
        print(error_message)
        raise Exception(error_message)


def create_document(index_name, document_data):
    try:
        index = meilisearch_client.index(index_name)
        index.add_documents([document_data])
        print(f"Created document in index {index_name}: {document_data}")
    except Exception as e:
        error_message = f"Error creating document in index {index_name}: {str(e)}"
        print(error_message)
        raise Exception(error_message)


def update_document(index_name, document_id, document_data):
    try:
        index = meilisearch_client.index(index_name)
        index.update_documents([{'id': document_id, 'update': document_data}])
        print(
            f"Updated document in index {index_name} with ID {document_id}: {document_data}")
    except Exception as e:
        error_message = f"Error updating document in index {index_name} with ID {document_id}: {str(e)}"
        print(error_message)
        raise Exception(error_message)


def delete_document(index_name, document_id):
    try:
        index = meilisearch_client.index(index_name)
        index.delete_documents([document_id])
        print(
            f"Deleted document with ID {document_id} from index {index_name}")
    except Exception as e:
        error_message = f"Error deleting document with ID {document_id} from index {index_name}: {str(e)}"
        print(error_message)
        raise Exception(error_message)

# Poll SQS and process messages


def poll_sqs_and_process_messages():
    print("Starting to pooling message ...")
    while True:
        try:
            # Receive up to 5 messages from the SQS queue
            response = sqs.receive_message(
                QueueUrl=queue_url,
                MaxNumberOfMessages=5,
                WaitTimeSeconds=20  # Adjust this as needed
            )

            messages = response.get('Messages', [])
            if messages:
                for message in messages:
                    process_sqs_message(message)
                    # Delete the processed message from the queue
                    sqs.delete_message(QueueUrl=queue_url,
                                       ReceiptHandle=message['ReceiptHandle'])
            else:
                print("No messages to process. Sleeping for a while...")
        except Exception as e:
            print("Error in message processing loop:", str(e))


# Start processing messages
poll_sqs_and_process_messages()


# Sample JSON messages for testing:

# 1. Create Index Message
create_index_message = {
    "type": "INDEX",
    "operation": "CREATE",
    "indexName": "products",
    "primaryKey": "product_id",
    "indexSettings": {
        "searchableAttributes": ["name", "description"],
        "rankingRules": ["typo", "words", "proximity"]
    }
}

# 2. Update Index Settings Message
update_index_settings_message = {
    "type": "INDEX",
    "operation": "UPDATE",
    "indexName": "products",
    "indexSettings": {
        "searchableAttributes": ["name", "description", "category"],
        "rankingRules": ["typo", "words"]
    }
}

# 3. Delete Index Message
delete_index_message = {
    "type": "INDEX",
    "operation": "DELETE",
    "indexName": "products"
}

# 4. Create Document Message
create_document_message = {
    "type": "DOCUMENT",
    "operation": "CREATE",
    "indexName": "products",
    "documentData": {
        "product_id": "12345",
        "name": "Sample Product",
        "description": "This is a sample product."
    }
}

# 5. Update Document Message
update_document_message = {
    "type": "DOCUMENT",
    "operation": "UPDATE",
    "indexName": "products",
    "documentId": "12345",
    "documentData": {
        "name": "Updated Product Name"
    }
}

# 6. Delete Document Message
delete_document_message = {
    "type": "DOCUMENT",
    "operation": "DELETE",
    "indexName": "products",
    "documentId": "12345"
}
