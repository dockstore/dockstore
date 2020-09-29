#!/usr/bin/env bash
# Checks that the generated THIRD-PARTY-LICENSES.txt matches the checked-in file
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" != "automated-review" ]; then
    exit 0;
fi

cp THIRD-PARTY-LICENSES.txt generated-licenses.txt
git checkout THIRD-PARTY-LICENSES.txt
diff generated-licenses.txt THIRD-PARTY-LICENSES.txt
