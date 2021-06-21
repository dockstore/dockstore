#!/usr/bin/env bash
# Installs dependencies for integration tests, not used for unit tests
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace
if [ "${TESTING_PROFILE}" = "language-parsing-tests" ]; then
    pip3 install aws-sam-cli
    sudo apt install unzip
    wget https://github.com/dockstore/lambda/releases/download/0.1.6-SNAPSHOT/wdl-parsing.zip
    unzip wdl-parsing.zip
    cd wdl-parsing
    # docker pull amazon/aws-sam-cli-build-image-java11
    # cannot seem to `sam build --use-container` using local jdk and mvn instead
    sam build
    sam local start-api &
    cd ..
    exit 0;
fi
if [ "${TESTING_PROFILE}" = "unit-tests" ] || [ "${TESTING_PROFILE}" == "automated-review" ]; then
    exit 0;
fi

if [ "${TESTING_PROFILE}" = "regression-integration-tests" ]; then
    pip3 install -r dockstore-webservice/src/main/resources/requirements/1.7.0/requirements3.txt
else
    pip3 install --user -r dockstore-webservice/src/main/resources/requirements/1.10.0/requirements3.txt
fi
