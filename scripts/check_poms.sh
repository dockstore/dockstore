#!/usr/bin/env bash
# Checks that generated flattened pom files were checked-in

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

GENERATED_POM_FILE_DIR=generated/src/main/resources/
if [ "${TESTING_PROFILE}" != "automated-review" ]; then
    exit 0;
fi

mvn validate
for filename in */${GENERATED_POM_FILE_DIR}pom.xml; do
  git diff --exit-code "$filename" || exit 1
done