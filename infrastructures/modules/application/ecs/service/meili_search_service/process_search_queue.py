import enum
import json
import boto3
import meilisearch
import ast

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
        print("Process message ", message_body)
        message_type = message_body.get("type")

        if message_type == "INDEX":
            process_index_message(message_body)
        elif message_type == "DOCUMENT":
            process_document_message(message_body)
        else:
            print("Unknown message type: %s", message_type)  # Log the error
    except Exception as e:
        print("Error processing message: %s", str(e))  # Log the error

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
        print("Unknown index operation: %s", operation)  # Log the error

# Process messages related to documents


def process_document_message(message):
    operation = message.get("operation")
    index_name = message.get("indexName")
    if operation == Operation.CREATE.value:
        document_data = message.get("documentData")
        create_document(index_name, fix_single_quotes(document_data))
    elif operation == Operation.UPDATE.value:
        document_id = message.get("documentId")
        document_data = message.get("documentData")
        update_document(index_name, document_id, fix_single_quotes(document_data))
    elif operation == Operation.DELETE.value:
        document_id = message.get("documentId")
        delete_document(index_name, document_id)
    else:
        print("Unknown document operation: %s", operation)  # Log the error

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

        print("Created index: %s", index_name)  # Log the info
    except Exception as e:
        error_message = f"Error creating index {index_name}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)

# Modify the update_index function to accept index_settings as a parameter


def update_index(index_name, index_settings):
    try:
        # Update the index settings with the provided settings
        meilisearch_client.index(index_name).update_settings(index_settings)
        print("Updated index settings for index %s", index_name)  # Log the info
    except Exception as e:
        error_message = f"Error updating index settings for index {index_name}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)


def delete_index(index_name):
    try:
        meilisearch_client.index(index_name).delete()
        print("Deleted index: %s", index_name)  # Log the info
    except Exception as e:
        error_message = f"Error deleting index {index_name}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)


def create_document(index_name, document_data):
    try:
        index = meilisearch_client.index(index_name)
        index.add_documents([document_data])
        print("Created document in index %s: %s", index_name, document_data)  # Log the info
    except Exception as e:
        error_message = f"Error creating document in index {index_name}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)


def update_document(index_name, document_id, document_data):
    try:
        index = meilisearch_client.index(index_name)
        index.update_documents([{'id': document_id, 'update': document_data}])
        print("Updated document in index %s with ID %s: %s", index_name, document_id, document_data)  # Log the info
    except Exception as e:
        error_message = f"Error updating document in index {index_name} with ID {document_id}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)


def delete_document(index_name, document_id):
    try:
        index = meilisearch_client.index(index_name)
        index.delete_documents([document_id])
        print("Deleted document with ID %s from index %s", document_id, index_name)  # Log the info
    except Exception as e:
        error_message = f"Error deleting document with ID {document_id} from index {index_name}: {str(e)}"
        print(error_message)  # Log the error
        raise Exception(error_message)

def fix_single_quotes(input_data):
    """
    Recursively replace single-quotes with double-quotes in all string values of a dictionary.

    Args:
        input_data (dict or str): The input data, which can be a dictionary or a JSON string.

    Returns:
        dict: The updated dictionary with single-quotes replaced by double-quotes.
    """
    if isinstance(input_data, str):
        try:
            # Attempt to parse the input as JSON
            input_dict = json.loads(input_data)
        except Exception as json_error:
            print("Error parsing input as JSON: %s", str(json_error))
            raise json_error
    elif isinstance(input_data, dict):
        input_dict = input_data
    else:
        print("Input is not a dictionary or JSON string: %s", input_data)
        raise ValueError("Input must be a dictionary or JSON string")

    # Now input_dict contains the dictionary to process
    for key, value in input_dict.items():
        if isinstance(value, dict):
            try:
                # Recursively process nested dictionaries
                input_dict[key] = fix_single_quotes(value)
            except Exception as e:
                print("Error processing nested dictionary: %s", str(e))
                raise e
        elif isinstance(value, list):
            try:
                # Recursively process lists of dictionaries
                input_dict[key] = [fix_single_quotes(item) if isinstance(item, dict) else item for item in value]
            except Exception as e:
                print("Error processing list of dictionaries: %s", str(e))
                raise e
        elif isinstance(value, str):
            try:
                # Replace single-quotes with double-quotes in string values
                input_dict[key] = value.replace("'", "\"")
            except Exception as e:
                print("Error replacing quotes in string: %s", str(e))
                raise e

    return input_dict

# Poll SQS and process messages


def poll_sqs_and_process_messages():
    print("Starting to polling message ...")
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
            print("Error in message processing loop: %s", str(e))  # Log the error


# Start processing messages
poll_sqs_and_process_messages()
