#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
set -o xtrace
max_attempts=30
attempt=1
until psql -h localhost -c '\l' -U postgres > /dev/null; do
    if [ $attempt -eq $max_attempts ]
    then
        echo "Postgres did not start in time"
        return 1
    else
        attempt=`expr "$attempt" + 1`
        sleep 1
    fi
done
echo "all done"
