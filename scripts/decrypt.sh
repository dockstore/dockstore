#!/usr/bin/env bash
# This script decrypts our test database
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io
# You will need the environment variables CIRCLE_CI_KEY and CIRCLE_CI_IV

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

openssl aes-256-cbc -d -in circle_ci_test_data.zip.enc -k "$CIRCLE_CI_KEY" -iv "$CIRCLE_CI_IV" -out secrets.tar
tar xvf secrets.tar

