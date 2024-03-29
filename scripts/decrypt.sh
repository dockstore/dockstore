#!/usr/bin/env bash
# This script decrypts our test database
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io
# You will need the environment variables CIRCLE_CI_WS_KEY and CIRCLE_CI_WS_IV
# This is tested with openssl 1.1.1 when running locally. Your mileage may vary with other versions

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

: "$CIRCLE_CI_WS_KEY"
: "$CIRCLE_CI_WS_IV"

openssl aes-256-cbc -md sha256 -d -in circle_ci_test_data.zip.enc -k "$CIRCLE_CI_WS_KEY" -iv "$CIRCLE_CI_WS_IV" -out secrets.tar
tar xvf secrets.tar
sudo mkdir -p /usr/local/ci
sudo cp dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem /usr/local/ci/dstesting_pcks8.pem
cat dockstore-integration-testing/src/test/resources/secretDockstoreTest.yml > dockstore-integration-testing/src/test/resources/dockstoreTest.yml
cat dockstore-integration-testing/src/test/resources/partialDockstoreTest.yml >> dockstore-integration-testing/src/test/resources/dockstoreTest.yml
rm secrets.tar

# remove any database dumps, which might have been generated from the non-confidential migrations
rm -f /tmp/dockstore_dump_*
