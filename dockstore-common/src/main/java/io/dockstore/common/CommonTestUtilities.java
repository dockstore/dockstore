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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.dbutils.handlers.KeyedHandler;
import org.apache.commons.io.FileUtils;

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

    private static class TestingPostgres extends BasicPostgreSQL {

        TestingPostgres(HierarchicalINIConfiguration config) {
            super(config);
        }

        @Override
        public void clearDatabase() {
            super.clearDatabase();
            runInsertStatement("insert into enduser(id, isAdmin, username) VALUES (1,true,'admin@admin.com');", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into token(id, content, tokensource, userid, username) VALUES (1, '" + DUMMY_TOKEN_1
                    + "', 'dockstore', 1, 'admin@admin.com');", new KeyedHandler<>("id"));

            runInsertStatement("insert into enduser(id, isAdmin, username) VALUES (2,false,'user1@user.com');", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into token(id, content, tokensource, userid, username) VALUES (2, '" + DUMMY_TOKEN_2
                    + "', 'dockstore', 2, 'user1@user.com');", new KeyedHandler<>("id"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered, toolname) VALUES (1, 'test1', 'test_org', 'QUAY_IO', 'quay.io/test_org/test1', false, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (1, 1);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered,toolname) VALUES (2, 'test2', 'test_org', 'QUAY_IO', 'quay.io/test_org/test2', false, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 2);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered,toolname) VALUES (3, 'test3', 'test_org', 'QUAY_IO', 'quay.io/test_org/test3', true, false, false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 3);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered, giturl,toolname) VALUES (4, 'test4', 'test_org', 'QUAY_IO', 'quay.io/test_org/test4', false, false, false, false, 'git@github.com:test/test4.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 4);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered, giturl,toolname) VALUES (5, 'test5', 'test_org', 'QUAY_IO', 'quay.io/test_org/test5', true, false, false, false, 'git@github.com:test/test5.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 5);", new KeyedHandler<>("containerid"));

            runInsertStatement(
                    "insert into container(id, name, namespace, registry, path, hasCollab, isstarred, ispublic, isregistered, giturl,toolname) VALUES (6, 'test6', 'test_org', 'QUAY_IO', 'quay.io/test_org/test6', true, false, false, true, 'git@github.com:test/test6.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (1, 6);", new KeyedHandler<>("containerid"));
            runInsertStatement("insert into usercontainer(userid, containerid) VALUES (2, 6);", new KeyedHandler<>("containerid"));
            // need to increment past manually entered ids above
            runUpdateStatement("alter sequence container_id_seq restart with 1000;");
        }
    }

    /**
     * Clears database state and known queues for testing.
     *
     * @throws IOException
     * @throws java.util.concurrent.TimeoutException
     */
    public static void clearState() throws IOException, TimeoutException {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        TestingPostgres postgres = new TestingPostgres(parseConfig);
        postgres.clearDatabase();
    }
}
