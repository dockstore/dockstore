#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace
set -o pipefail

rm -Rf tool-registry-validator
git clone https://github.com/ga4gh/tool-registry-validator.git
cd tool-registry-validator
git checkout feature/dockstore_work
python setup.py install --user
wget https://raw.githubusercontent.com/ga4gh/tool-registry-schemas/1.0.0/src/main/resources/swagger/ga4gh-tool-discovery.yaml
ga4gh-tool-registry-validate ga4gh-tool-discovery.yaml annotations.yml http://localhost:$1/api/ga4gh/v2/tools

