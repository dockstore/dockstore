#!/usr/bin/env bash
# this takes the confidential data from the appropriate places in our build directories and puts it all into one folder for uploading to Google Drive
# warning: do not commit this data accidentally!
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

# ex. bash prepare_confidential_upload.sh abandonDatabaseConnections

zipFolder=$1
mkdir ${zipFolder}
cp -t ${zipFolder} dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential1.xml dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml dockstore-integration-testing/src/test/resources/dockstoreTest.yml dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem
