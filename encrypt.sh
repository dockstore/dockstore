#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace


tar cvf dockstore-integration-testing/src/test/resources/secrets.tar dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/db_confidential_dump.sql dockstore-integration-testing/src/test/resources/dockstoreTest.yml dockstore-integration-testing/src/test/resources/db_confidential_dump_full.sql

# store working dir
GIT_DIR=`pwd`
# execute always in the same place to keep generated variable names consistent (sigh)
cd /tmp 
travis encrypt-file $GIT_DIR/dockstore-integration-testing/src/test/resources/secrets.tar -r ga4gh/dockstore
# copy the new file
cp secrets.tar.enc $GIT_DIR/dockstore-integration-testing/src/test/resources
cd - 

git add dockstore-integration-testing/src/test/resources/secrets.tar.enc
git commit -m 'update secret archive'
git push
