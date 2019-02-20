#!/usr/bin/env bash
# Checks that the JPA classes are consistent with the liquibase migrations
# Includes Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [ "${TESTING_PROFILE}" = "automated-review" ]; then
    bash propose_migration.sh
    ! grep "changeSet" dockstore-webservice/target/detected-migrations.xml
    exit 0;
fi
