#!/usr/bin/env bash
# Installs dependencies for integration tests, not used for unit tests
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace
if [ "${TESTING_PROFILE}" = "unit-tests" ] || [ "${TESTING_PROFILE}" == "automated-review" ]; then
    exit 0;
fi

if [ "${TESTING_PROFILE}" = "regression-integration-tests" ]; then
    pip3 install -r dockstore-webservice/src/main/resources/requirements/1.13.0/requirements3.txt
elif [ "${TESTING_PROFILE}" == "language-parsing-tests" ]; then
    #pip3 install -r dockstore-webservice/src/main/resources/requirements/1.14.0/requirements3.txt
    sudo apt-get update
    # https://stackoverflow.com/questions/44331836/apt-get-install-tzdata-noninteractive needed by cwltool
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y tzdata
    sudo apt-get -qq --yes --force-yes install cwltool
else
    pip3 install --user -r dockstore-webservice/src/main/resources/requirements/1.14.0/requirements3.txt
fi
