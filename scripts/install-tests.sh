#!/usr/bin/env bash
# Installs dependencies for integration tests, not used for unit tests
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace
curl -fsSL "https://raw.githubusercontent.com/Linuxbrew/install/master/install.sh" | bash
echo 'eval $(/home/linuxbrew/.linuxbrew/bin/brew shellenv)' >> /home/travis/.bash_profile
eval $(/home/linuxbrew/.linuxbrew/bin/brew shellenv)
brew tap aws/tap
brew install aws-sam-cli
sam --version
cd wdl-parsing
docker pull amazon/aws-sam-cli-build-image-java11
sam build --use-container
sam local start-api &
cd ..
if [ "${TESTING_PROFILE}" = "unit-tests" ] || [ "${TESTING_PROFILE}" == "automated-review" ]; then
    exit 0;
fi

if [ "${TESTING_PROFILE}" = "regression-integration-tests" ]; then
    pip3 install --user -r dockstore-webservice/src/main/resources/requirements/1.6.0/requirements3.txt
else
    pip3 install --user -r dockstore-webservice/src/main/resources/requirements/1.7.0/requirements3.txt
fi

mvn -B clean install -DskipTests -ntp


# hook up integration tests with elastic search
docker pull elasticsearch:5.6.3
docker run -p 9200:9200 -d elasticsearch:5.6.3
