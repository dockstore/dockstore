#!/usr/bin/env bash
# This script is used to encrypt the confidential test data
# You will need the environment variables CIRCLE_CI_KEY_2 and CIRCLE_CI_IV_2 populated

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

: "$CIRCLE_CI_KEY_2"
: "$CIRCLE_CI_IV_2"

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-integration-testing/src/test/resources/secretDockstoreTest.yml dockstore-webservice/src/main/resources/migrations.test.confidential1.xml dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml

openssl aes-256-cbc -md sha256 -e -in secrets.tar -out circle_ci_test_data.zip.enc -k "$CIRCLE_CI_KEY_2" -iv "$CIRCLE_CI_IV_2"

rm secrets.tar

