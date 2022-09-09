#!/bin/bash
if [ "$#" -lt 2 ]; then
  echo "Usage: $0 ENVIRONMENTNAME[ 'dev' | 'prod' ] [TERRA_FORM_COMMAND]" >&2
  exit 1
fi

CUR_DIR=$(pwd)
ENV=${1}
shift
TF_COMAND=${@}

if [ "${ENV}" != "dev" ] && [ "${ENV}" != "prod" ]; then
  echo "Usage: $0 ENVIRONMENTNAME[ 'dev' | 'prod' ] [TERRA_FORM_COMMAND]" >&2
  exit 1
fi

# if ! command -v terraform &> /dev/null
# then
#   echo "terraform command could not be found. Please activate it!"
#   exit
# fi

cd ./infrastructures/environments/${ENV}
ln -sf ../../main.tf .
ln -sf ../../output.tf .
ln -sf ../../serverless.conf serverless.auto.tfvars
cd ${CUR_DIR}

echo "Running terraform -chdir=./infrastructures/environments/${ENV} ${TF_COMAND}"
terraform -chdir="./infrastructures/environments/${ENV}" ${TF_COMAND}

if [ $? -ne 0 ]; then
  echo "Failed to run terraform command [${TF_COMAND}]!"
  exit 1
fi
