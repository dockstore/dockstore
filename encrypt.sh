#!/usr/bin/env bash
# This script is used to encrypt the confidential test data
# You will need the environment variables CIRCLE_CI_WS_KEY and CIRCLE_CI_WS_IV populated

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

: "$CIRCLE_CI_WS_KEY"
: "$CIRCLE_CI_WS_IV"

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-webservice/src/main/resources/migrations.test.confidential4.xml dockstore-integration-testing/src/test/resources/secretDockstoreTest.yml dockstore-webservice/src/main/resources/migrations.test.confidential1.xml dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential4_1.5.0.xml

openssl aes-256-cbc -md sha256 -e -in secrets.tar -out circle_ci_test_data.zip.enc -k "$CIRCLE_CI_WS_KEY" -iv "$CIRCLE_CI_WS_IV"

rm secrets.tar

