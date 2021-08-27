#!/bin/bash

PSQL="docker exec -it -u postgres -e PGPASSWORD=dockstore postgres1 psql -d webservice_test -c"
ORG_ID=123456
COL_ID=1234567
USER_ID=12345678

$PSQL "DELETE FROM organization WHERE id = $ORG_ID;"
$PSQL "INSERT INTO organization (id, name, displayname, description, status) VALUES ($ORG_ID, 'dscurated', 'Dockstore Curated', 'Dockstore Curated', 'SPECIAL');"

$PSQL "DELETE FROM collection WHERE id = $COL_ID;"
$PSQL "INSERT INTO collection (id, name, displayname, organizationid, topic, description) VALUES ($COL_ID, 'foo', 'Foo Collection', $ORG_ID, 'The Foo Collection', 'Description of the Foo Collection');"

$PSQL "DELETE FROM enduser WHERE id = $USER_ID;"
$PSQL "INSERT INTO enduser (id, isadmin, username) VALUES ($USER_ID, TRUE, 'dscurator');"

$PSQL "UPDATE enduser SET isadmin=true, curator=true WHERE id = 62307;"

$PSQL "INSERT INTO collection_entry_version (id, entry_id, version_id, collection_id) VALUES (77777777, 98, 83175, $COL_ID);"

$PSQL "INSERT INTO organization_user (organizationid, userid, accepted, role) VALUES ($ORG_ID, $USER_ID, TRUE, 'ADMIN');"
$PSQL "INSERT INTO organization_user (organizationid, userid, accepted, role) VALUES ($ORG_ID, 62307, TRUE, 'ADMIN');"
$PSQL "INSERT INTO organization_user (organizationid, userid, accepted, role) VALUES (16, 62307, TRUE, 'ADMIN');"

$PSQL "UPDATE enduser SET isadmin = true WHERE username = 'svonworl'";


