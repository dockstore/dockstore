#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/confidential_test.txt
# store working dir
GIT_DIR=`pwd`
# execute always in the same place to keep generated variable names consistent (sigh)
cd /tmp 
travis encrypt-file $GIT_DIR/secrets.tar -r ga4gh/dockstore
# copy the new file
cp secrets.tar.enc $GIT_DIR
cd - 

git add secrets.tar.enc
git commit -m 'update secret archive'
git push
