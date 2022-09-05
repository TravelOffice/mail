#!/bin/bash
if [ "$#" -lt 1 ]; then
  echo "Usage: $0 ENVIRONMENTNAME[ 'dev' | 'prod' ]" >&2
  exit 1
fi

CUR_DIR=$(pwd)
ENV=${1}

build_service() {
  SERVICENAME=$1
  SERVICE_DIR=${CUR_DIR}/src/modules/${SERVICENAME}
  # Build service
  echo "Build service ${SERVICENAME}..."
  cd ${SERVICE_DIR}
  if [ ! -f "build.gradle.kts" ]; then
    echo "Missing requirement for ${ENV}!" >&2
    return 1
  fi
  ./gradlew build
  if [ $? -ne 0 ]; then
      echo "Failed to build service ${SERVICENAME}!"
      exit 1
  fi
}

cd ${CUR_DIR}/src/modules
MODULES_DIR=$(ls -d */ | cut -f1 -d'/')
for dir in $MODULES_DIR
do
  build_service ${dir}
done
