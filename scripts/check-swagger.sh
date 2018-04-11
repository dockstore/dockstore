#!/usr/bin/env bash
# This script checks that the generated swagger.yaml is semantically equivalent to the checked in yaml
# Equivalent to a integration test
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" = "unit-tests" ]; then
    exit 0;
fi

cp dockstore-webservice/src/main/resources/swagger.yaml generated.yaml
git checkout dockstore-webservice/src/main/resources/swagger.yaml
go get -v github.com/bronze1man/yaml2json
cat dockstore-webservice/src/main/resources/swagger.yaml | yaml2json | python -m json.tool > t1.json
cat generated.yaml | yaml2json | python -m json.tool > t2.json
diff t1.json t2.json
