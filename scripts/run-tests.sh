#!/bin/bash
set -ev

echo "${TRAVIS_BRANCH}"
echo "${TRAVIS_PULL_REQUEST}"
echo "${TRAVIS_PULL_REQUEST_BRANCH}"
echo "${TRAVIS_PULL_REQUEST_SHA}"

if [ "${TESTING_PROFILE}" = "integration-tests" ]; then
    openssl aes-256-cbc -K $encrypted_4235c77db5dc_key -iv $encrypted_4235c77db5dc_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
fi

if [ ${#EXTRA_MAVEN_VAR} -eq 0 ]; then
        # always do non-coverage builds
    echo "Non-coverage build"
	mvn --batch-mode clean install -P${TESTING_PROFILE} ${EXTRA_MAVEN_VAR}
else
    mvn --batch-mode dependency:get -Dartifact=org.eluder.coveralls:coveralls-maven-plugin:4.2.0
    # for coverage builds, we need to be more picky, let's exclude builds from branches other than develop and master
    if [ "${TRAVIS_BRANCH}" = "master" ] || [ "${TRAVIS_BRANCH}" = "develop" ]; then
        echo "Coverage build"
        mvn --batch-mode clean install -P${TESTING_PROFILE} ${EXTRA_MAVEN_VAR}
    fi
fi
