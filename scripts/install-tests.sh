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
    pip3 install -r dockstore-webservice/src/main/resources/requirements/1.15.0/requirements3.txt
elif [ "${TESTING_PROFILE}" == "language-parsing-tests" ]; then
    # depending on https://github.com/dockstore/dockstore/pull/5958 we may want to match where we go with the cwltool install, for now apt seems to work well
    sudo apt-get update
    # https://stackoverflow.com/questions/44331836/apt-get-install-tzdata-noninteractive needed by cwltool
    DEBIAN_FRONTEND=noninteractive sudo apt-get -qq --yes --force-yes install tzdata
    sudo apt-get -qq --yes --force-yes install cwltool=3.1.20250110105449-3
else
    pip3 install --user -r dockstore-webservice/src/main/resources/requirements/1.14.0/requirements3.txt
fi
