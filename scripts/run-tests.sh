#!/bin/bash
set -ev

echo "${TRAVIS_BRANCH}"
echo "${TRAVIS_PULL_REQUEST}"
echo "${TRAVIS_PULL_REQUEST_BRANCH}"
echo "${TRAVIS_PULL_REQUEST_SHA}"

if [ "${TESTING_PROFILE}" = "integration-tests" ] || [ "${TESTING_PROFILE}" = "regression-integration-tests" ]; then
    openssl aes-256-cbc -K $encrypted_7e2d6ff95e70_key -iv $encrypted_7e2d6ff95e70_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
fi

mvn --batch-mode clean install -P${TESTING_PROFILE} ${EXTRA_MAVEN_VAR}
