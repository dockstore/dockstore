#!/usr/bin/env bash
# This script decrypts our test database 
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" = "integration-tests" ] || [ "${TESTING_PROFILE}" = "toil-integration-tests" ]; then
    openssl aes-256-cbc -K $encrypted_6006c24f51c8_key -iv $encrypted_6006c24f51c8_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
fi
