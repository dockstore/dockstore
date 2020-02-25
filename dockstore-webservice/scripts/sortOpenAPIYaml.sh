#!/usr/bin/env bash
set -o errexit
set -o pipefail
set -o nounset
# https://github.com/mikefarah/yq Require version 3.1.2.  Newer version will likely also work.
yq r --tojson src/main/resources/openapi3/unsortedopenapi.yaml | yq r - -P > src/main/resources/openapi3/openapi.yaml
