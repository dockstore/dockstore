#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-integration-testing/src/test/resources/dockstoreTest.yml dockstore-webservice/src/main/resources/migrations.test.confidential1.xml dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml

openssl aes-256-cbc -e -in secrets.tar -out circle_ci_test_data.zip.enc -k $CIRCLE_CI_KEY -iv $CIRCLE_CI_IV

rm secrets.tar
