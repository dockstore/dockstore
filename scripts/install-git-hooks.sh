#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

git config --add core.hookspath test
git config --unset-all core.hookspath
git secrets --register-aws
git config --add core.hookspath git-hooks/
