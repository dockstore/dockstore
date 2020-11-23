#!/usr/bin/env bash
# This script decrypts our test database 
# WARNING: Edit decrypt.template.mustache not decrypt.sh which is a generated file
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

openssl aes-256-cbc -d -in circle_ci_test_data.zip.enc -k $CIRCLE_CI_KEY -iv $CIRCLE_CI_IV -out secrets.tar
tar xvf secrets.tar
#mv dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem /home/travis/dstesting_pcks8.pem
