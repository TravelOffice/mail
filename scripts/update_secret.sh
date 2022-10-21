#!/bin/bash
SECRET_MANAGER_ID=${1}
SEARCH_ENGINE_ENDPOINT=${2}
SEARCH_ENGINE_MASTER_KEY=${3}

SECRET_VALUES=$(aws secretsmanager get-secret-value --secret-id ${SECRET_MANAGER_ID} --query SecretString)

# Remove SEARCH_ENGINE_ENDPOINT
SECRET_VALUES=$(echo ${SECRET_VALUES} | sed 's/,\\"SEARCH_ENGINE_ENDPOINT\\":\\".*\\"//g')
SECRET_VALUES=$(echo ${SECRET_VALUES} | sed 's/,\\"SEARCH_ENGINE_MASTER_KEY\\":\\".*\\"//g')
SECRET_VALUES=$(echo ${SECRET_VALUES} | sed 's/}"//g' | sed 's/^"//g')

SECRET_VALUES="${SECRET_VALUES},\\\"SEARCH_ENGINE_ENDPOINT\\\":\\\"${SEARCH_ENGINE_ENDPOINT}\\\""
SECRET_VALUES="${SECRET_VALUES},\\\"SEARCH_ENGINE_MASTER_KEY\\\":\\\"${SEARCH_ENGINE_MASTER_KEY}\\\""
SECRET_VALUES="${SECRET_VALUES}}"
SECRET_VALUES=$(echo ${SECRET_VALUES} | sed 's/\\//g')

aws secretsmanager put-secret-value --secret-id ${SECRET_MANAGER_ID} --secret-string "${SECRET_VALUES}"
