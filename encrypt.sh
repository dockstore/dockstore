#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

regex="encrypted_([0-9a-zA-Z]+)_key"

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/dstesting_pcks8.pem dockstore-integration-testing/src/test/resources/config_file.txt dockstore-integration-testing/src/test/resources/config_file2.txt dockstore-webservice/src/main/resources/migrations.test.confidential2.xml dockstore-integration-testing/src/test/resources/dockstoreTest.yml dockstore-webservice/src/main/resources/migrations.test.confidential1.xml dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml

# store working dir
GIT_DIR=`pwd`
# execute always in the same place to keep generated variable names consistent (sigh)
# go to a nested dir for the new db testing to maintain a consistent variable name
CUSTOM_DIR_NAME=github_app_and_services_3
rm -Rf /tmp/$CUSTOM_DIR_NAME
mkdir -p /tmp/$CUSTOM_DIR_NAME

cp $GIT_DIR/scripts/decrypt.template.mustache /tmp/$CUSTOM_DIR_NAME
cd /tmp/$CUSTOM_DIR_NAME
ENCRYPT_FILE_CONTENTS=`travis encrypt-file $GIT_DIR/secrets.tar -r dockstore/dockstore`

if [[ $ENCRYPT_FILE_CONTENTS =~ $regex ]]
    then
        name="${BASH_REMATCH[1]}"
        echo "name: ${name}" &> data.yml
        mustache data.yml decrypt.template.mustache > decrypt.sh
    else
        echo "uh oh, could not find encrypted variable name" 
        exit
fi

# copy the new file and run-script
cp secrets.tar.enc $GIT_DIR
cp decrypt.sh $GIT_DIR/scripts/decrypt.sh
cd - 
git add secrets.tar.enc $GIT_DIR/scripts/decrypt.sh $GIT_DIR/encrypt.sh
git commit -m 'update secret archive'
rm -rf /tmp/$CUSTOM_DIR_NAME
