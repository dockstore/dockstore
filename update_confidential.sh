#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

# this script generates a proposed database changelog file based on your current JPA classes

## destroy and recreate both databases
sudo -i -u postgres dropdb webservice_test || true
sudo -i -u postgres dropdb webservice_test_proposed || true
sudo -i -u postgres createdb webservice_test || true
sudo -i -u postgres createdb webservice_test_proposed || true
rm dockstore-webservice/target/detected-migrations.xml || true

## load up the old database based on current migration
rm dockstore-webservice/target/dockstore-webservice-*sources.jar || true
java -jar dockstore-webservice/target/dockstore-webservice-*.jar db migrate dockstore-integration-testing/src/test/resources/dockstoreTest.yml --include 1.3.0.generated,1.4.0
## create the new database based on JPA (ugly, should really create a proper dw command if this works)
timeout 15 java -Ddw.database.url=jdbc:postgresql://localhost:5432/webservice_test_proposed -Ddw.database.properties.hibernate.hbm2ddl.auto=create -jar dockstore-webservice/target/dockstore-webservice-*.jar server dockstore-integration-testing/src/test/resources/dockstoreTest.yml || true

cd dockstore-webservice && mvn liquibase:diff

echo 'examine proposed changes at `dockstore-webservice/target/detected-migrations.xml`'
