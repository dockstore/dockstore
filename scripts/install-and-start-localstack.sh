#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

python3 -m pip install localstack==1.3.1
localstack start