#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

# this script updates the databse schema and confidential database dumps
# use it when the database changes and we need to update our tests
# this relies on migrations.xml so update that with your proposed changes first

for fileName in db_confidential_dump_full.sql db_confidential_dump_full_2.sql ; do

    # destroy the current database
    sudo -i -u postgres dropdb webservice_test || true
    # create the database
    sudo -i -u postgres createdb webservice_test || true
    # load up the current schema
    cat dockstore-integration-testing/src/test/resources/schema.sql | sudo -i -u postgres psql webservice_test
    # load up the old confidential dump
    cat dockstore-integration-testing/src/test/resources/$fileName |  sudo -i -u postgres psql webservice_test
    
    # perform the migration
    rm dockstore-webservice/target/dockstore-webservice-*sources.jar || true
    java -jar dockstore-webservice/target/dockstore-webservice-*.jar db migrate dockstore-integration-testing/src/test/resources/dockstoreTest.yml

    # dump the database and save it (you'll want just the data with column inserts)
    sudo -i -u postgres pg_dump --data-only --inserts --column-inserts  webservice_test | tee /tmp/test1.sql > /dev/null
    # backup existing dump
    cp dockstore-integration-testing/src/test/resources/$fileName dockstore-integration-testing/src/test/resources/$fileName.bak
    # copy the results on top of one of the existing dumps
    cp /tmp/test1.sql dockstore-integration-testing/src/test/resources/$fileName

done




# backup existing schema 
cp dockstore-integration-testing/src/test/resources/schema.sql dockstore-integration-testing/src/test/resources/schema.sql.bak
# dump the schema 
sudo -i -u postgres pg_dump webservice_test --schema-only | tee dockstore-integration-testing/src/test/resources/schema.sql > /dev/null

diff dockstore-integration-testing/src/test/resources/db_confidential_dump_full.sql dockstore-integration-testing/src/test/resources/db_confidential_dump_full.sql.bak
diff dockstore-integration-testing/src/test/resources/db_confidential_dump_full_2.sql dockstore-integration-testing/src/test/resources/db_confidential_dump_full_2.sql.bak
diff dockstore-integration-testing/src/test/resources/schema.sql  dockstore-integration-testing/src/test/resources/schema.sql.bak
