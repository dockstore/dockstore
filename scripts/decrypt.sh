#!/usr/bin/env bash
# This script decrypts our test database
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io
# You will need the environment variables CIRCLE_CI_KEY and CIRCLE_CI_IV

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

openssl aes-256-cbc -d -in circle_ci_test_data.zip.enc -k "$CIRCLE_CI_KEY" -iv "$CIRCLE_CI_IV" -out secrets.tar
tar xvf secrets.tar
sudo mkdir -p /home/travis
sudo cp dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem /home/travis/dstesting_pcks8.pem
cat dockstore-integration-testing/src/test/resources/partialDockstoreTest.yml >> dockstore-integration-testing/src/test/resources/dockstoreTest.yml
rm secrets.tar

