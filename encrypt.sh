#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

tar cvf dockstore-integration-testing/src/test/resources/secrets.tar dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/db_dump.sql dockstore-integration-testing/src/test/resources/dockstoreTest.yml
travis encrypt-file dockstore-integration-testing/src/test/resources/secrets.tar
mv secrets.tar.enc dockstore-integration-testing/src/test/resources/secrets.tar.enc
git add dockstore-integration-testing/src/test/resources/secrets.tar.enc
git commit -m 'update secret archive'
git push
