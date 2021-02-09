#!/usr/bin/env bash
# This script decrypts our test database
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io
# You will need the environment variables CIRCLE_CI_KEY and CIRCLE_CI_IV
# Run from /dockstore. "bash scripts/macOS-decrypt.sh"

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace


docker run -it --volume $(pwd):/home/project --env CIRCLE_CI_KEY --env CIRCLE_CI_IV  ubuntu:latest bin/bash home/project/scripts/decrypt-in-ubuntu-container.sh
