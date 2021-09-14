#!/bin/bash

PSQL="docker exec -it -u postgres -e PGPASSWORD=dockstore postgres1 psql -qAtX -d webservice_test -c"

echo "Add hidden, categorizer to organization table"
$PSQL "ALTER TABLE organization ADD categorizer BOOLEAN NOT NULL DEFAULT FALSE;"

echo "Adding categories organization"

$PSQL "INSERT INTO organization (name, displayname, description, status, categorizer) VALUES ('dockstoreCategories', 'Dockstore Categories', 'Categories in Dockstore', 'HIDDEN', true);"
ORG_ID=$($PSQL "SELECT id FROM organization WHERE name = 'dockstoreCategories'" | tr -d '\r')

echo "Categories organization ID: $ORG_ID"

for username in "$@"
do
    USER_ID=$($PSQL "SELECT id FROM enduser WHERE username = '$username'" | tr -d '\r')
    echo "Adding User ID $USER_ID as admin for categories organization"
    $PSQL "INSERT INTO organization_user (organizationid, userid, accepted, role) VALUES ($ORG_ID, $USER_ID, true, 'ADMIN')"
done

echo "Add DTYPE column to collection table"
$PSQL "ALTER TABLE collection ADD dtype varchar DEFAULT 'Collection';"
$PSQL "create unique index collection_categoryname_index on collection (LOWER(name)) where dtype = 'Category';"
