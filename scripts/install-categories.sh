#!/bin/bash

PSQL="docker exec -it -u postgres -e PGPASSWORD=dockstore postgres1 psql -qAtX -d webservice_test -c"

echo "Adding categories organization"

$PSQL "INSERT INTO organization (name, displayname, description, status) VALUES ('dockstoreCategories', 'Dockstore Categories', 'Categories in Dockstore', 'SPECIAL');"
ORG_ID=$($PSQL "SELECT id FROM organization WHERE name = 'dockstoreCategories'" | tr -d '\r')

echo "Categories organization ID: $ORG_ID"

for username in "$@"
do
    USER_ID=$($PSQL "SELECT id FROM enduser WHERE username = '$username'" | tr -d '\r')
    echo "Adding User ID $USER_ID as admin for categories organization"
    $PSQL "INSERT INTO organization_user (organizationid, userid, accepted, role) VALUES ($ORG_ID, $USER_ID, true, 'ADMIN')"
done


