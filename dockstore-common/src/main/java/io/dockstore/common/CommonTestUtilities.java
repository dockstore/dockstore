/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.common;

import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.KeyedHandler;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;


/**
 *
 * @author xliu
 */
public class CommonTestUtilities {
    public static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";
    /**
     * A key used for testing
     */
    public static final String DUMMY_TOKEN_2 = "3a04647fd0a1bd949637n5fddb164261fc8c80d83f0750fe0e873bc744338fce";

    public static HierarchicalINIConfiguration parseConfig(String path) {
        try {
            return new HierarchicalINIConfiguration(path);
        } catch (ConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static class TestingPostgres extends BasicPostgreSQL {

        TestingPostgres(HierarchicalINIConfiguration config) {
            super(config);
        }

        @Override
        public void clearDatabase() {
            super.clearDatabase();
            runInsertStatement("insert into enduser(id, isAdmin, username) VALUES (1,true,'admin@admin.com');", new KeyedHandler<>("id"));
            runInsertStatement("insert into token(id, content, tokensource, userid, username) VALUES (1, '" + DUMMY_TOKEN_1
                    + "', 'dockstore', 1, 'admin@admin.com');", new KeyedHandler<>("id"));

            runInsertStatement("insert into enduser(id, isAdmin, username) VALUES (2,false,'user1@user.com');", new KeyedHandler<>("id"));
            runInsertStatement("insert into token(id, content, tokensource, userid, username) VALUES (2, '" + DUMMY_TOKEN_2
                    + "', 'dockstore', 2, 'user1@user.com');", new KeyedHandler<>("id"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered, toolname) VALUES (1, 'test1', 'test_org', 'QUAY_IO', 'quay.io/test_org/test1', false, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (1, 1);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered,toolname) VALUES (2, 'test2', 'test_org', 'QUAY_IO', 'quay.io/test_org/test2', false, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 2);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered,toolname) VALUES (3, 'test3', 'test_org', 'QUAY_IO', 'quay.io/test_org/test3', true, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 3);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered, giturl,toolname) VALUES (4, 'test4', 'test_org', 'QUAY_IO', 'quay.io/test_org/test4', false, false, false, false, 'git@github.com:test/test4.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 4);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered, giturl,toolname) VALUES (5, 'test5', 'test_org', 'QUAY_IO', 'quay.io/test_org/test5', true, false, false, false, 'git@github.com:test/test5.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 5);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, validTrigger, isstarred, ispublic, isregistered, giturl,toolname) VALUES (6, 'test6', 'test_org', 'QUAY_IO', 'quay.io/test_org/test6', true, false, false, true, 'git@github.com:test/test6.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (1, 6);", new KeyedHandler<>("containerid"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 6);", new KeyedHandler<>("containerid"));

            runInsertStatement("insert into tag(id, valid, automated, hidden, size) VALUES (1, true, true, false, 0);", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into containertag(containerid, tagid) VALUES (6, 1);", new KeyedHandler<>("tagid"));

            runInsertStatement("insert into tag(id, valid, automated, hidden, size) VALUES (2, true, true, false, 0);", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into containertag(containerid, tagid) VALUES (5, 2);", new KeyedHandler<>("tagid"));

            // need to increment past manually entered ids above
            runUpdateStatement("alter sequence container_id_seq restart with 1000;");
            runUpdateStatement("alter sequence tag_id_seq restart with 1000;");
        }

        public void clearDatabaseMakePrivate() throws IOException {
            super.clearDatabase();

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(ResourceHelpers.resourceFilePath("db_confidential_dump.sql")), "utf-8"));
            String line = null;

            while ((line = br.readLine()) != null) {
                runUpdateStatementConfidential(line);
            }
            br.close();

        }

        @Override
        public <T> T runSelectStatement(String query, ResultSetHandler<T> handler, Object... params) {
            return super.runSelectStatement(query, handler, params);
        }

        @Override
        public boolean runUpdateStatement(String query, Object... params){
            return super.runUpdateStatement(query, params);
        }
    }

    /**
     * Clears database state and known queues for testing.
     **/
    public static void clearState() {
        final TestingPostgres postgres = getTestingPostgres();
        postgres.clearDatabase();
    }

    /**
     * Clears database state and known queues for confidential testing.
     * @throws IOException
         */
    public static void clearStateMakePrivate() throws IOException {
        final TestingPostgres postgres = getTestingPostgres();
        postgres.clearDatabaseMakePrivate();

    }

    public static TestingPostgres getTestingPostgres() {
        final File configFile = FileUtils.getFile("src", "test", "resources", "config");
        final HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        return new TestingPostgres(parseConfig);
    }
}
