#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

tar cvf secrets.tar dockstore-integration-testing/src/test/resources/confidential_test.txt
travis encrypt-file secrets.tar
git add secrets.tar.enc
git commit -m 'update secret archive'
git push
