#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace


tar cvf secrets.tar dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-integration-testing/src/test/resources/dockstoreTest.yml dockstore-webservice/src/main/resources/migrations.test.confidential1.xml

# store working dir
GIT_DIR=`pwd`
# execute always in the same place to keep generated variable names consistent (sigh)
# go to a nested dir for the new db testing to maintain a consistent variable name
CUSTOM_DIR_NAME=db_with_changelog
mkdir -p /tmp/$CUSTOM_DIR_NAME
cd /tmp/$CUSTOM_DIR_NAME
travis encrypt-file $GIT_DIR/secrets.tar -r ga4gh/dockstore
# copy the new file
cp secrets.tar.enc $GIT_DIR
cd - 

git add secrets.tar.enc
git commit -m 'update secret archive'
