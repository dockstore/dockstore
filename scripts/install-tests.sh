#!/usr/bin/env bash
# Installs dependencies for integration tests, not used for unit tests
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" = "unit-tests" ]; then
    exit 0;
fi

if [ "${TESTING_PROFILE}" = "toil-integration-tests" ]; then
    pip2.7 install --user toil[cwl]==3.14.0
else
    pip install --user localstack
    localstack start &
    pip2.7 install --user setuptools==36.5.0
    pip2.7 install --user cwl-runner cwltool==1.0.20170828135420 schema-salad==2.6.20170806163416 avro==1.8.1 ruamel.yaml==0.14.12 requests==2.18.4
fi
