#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
# https://github.com/mikefarah/yq Require version 3.1.2.  Newer version will likely also work.
yq r --tojson src/main/resources/openapi3/unsortedopenapi.yaml | yq r - -P > src/main/resources/openapi3/openapi.yaml || {
  exitcode=$?
  if [[ $exitcode -eq 127 ]]; then
    echo "ERROR: 127 - Missing build dependency yq - install 3.1.2 or later at https://github.com/mikefarah/yq"
  fi
  exit $exitcode
}
