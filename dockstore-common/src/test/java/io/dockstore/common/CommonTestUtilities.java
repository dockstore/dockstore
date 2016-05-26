/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.KeyedHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;

import io.dropwizard.testing.ResourceHelpers;


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

            //TODO: this stuff should probably use JPA statements
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished, toolname) VALUES (1, 'test1', 'test_org', 'QUAY_IO', 'quay.io/test_org/test1', false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (1, 1);", new KeyedHandler<>("entryid"));
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished,toolname) VALUES (2, 'test2', 'test_org', 'QUAY_IO', 'quay.io/test_org/test2', false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 2);", new KeyedHandler<>("entryid"));
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished,toolname) VALUES (3, 'test3', 'test_org', 'QUAY_IO', 'quay.io/test_org/test3', false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 3);", new KeyedHandler<>("entryid"));
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished, giturl,toolname) VALUES (4, 'test4', 'test_org', 'QUAY_IO', 'quay.io/test_org/test4', false, false, 'git@github.com:test/test4.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 4);", new KeyedHandler<>("entryid"));
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished, giturl,toolname) VALUES (5, 'test5', 'test_org', 'QUAY_IO', 'quay.io/test_org/test5', false, false, 'git@github.com:test/test5.git','');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 5);", new KeyedHandler<>("entryid"));
            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished, giturl,toolname) VALUES (6, 'test6', 'test_org', 'QUAY_IO', 'quay.io/test_org/test6', false, true, 'git@github.com:test/test6.git','');",
                    new KeyedHandler<>("id"));

            runInsertStatement("insert into user_entry(userid, entryid) VALUES (1, 6);", new KeyedHandler<>("entryid"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 6);", new KeyedHandler<>("entryid"));

            runInsertStatement("insert into tag(id, valid, automated, hidden, size, cwlpath, wdlpath, dockerfilepath) VALUES (1, true, true, false, 0,'/Dockstore.cwl', '/Dockstore.wdl', '/Dockerfile');", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into tool_tag(toolid, tagid) VALUES (6, 1);", new KeyedHandler<>("tagid"));

            runInsertStatement("insert into tag(id, valid, automated, hidden, size, cwlpath, wdlpath, dockerfilepath) VALUES (2, true, true, false, 0,'/Dockstore.cwl', '/Dockstore.wdl', '/Dockerfile');", new KeyedHandler<>(
                    "id"));
            runInsertStatement("insert into tool_tag(toolid, tagid) VALUES (5, 2);", new KeyedHandler<>("tagid"));

            // need to increment past manually entered ids above
            runUpdateStatement("alter sequence container_id_seq restart with 1000;");
            runUpdateStatement("alter sequence tag_id_seq restart with 1000;");
        }

        public void clearDatabaseMakePrivate() throws IOException {
            super.clearDatabase();

            runInsertDump(ResourceHelpers.resourceFilePath("db_confidential_dump_full.sql"));


            /*
             Todo: When features that require multiple users for testing, which depend on other sources such as Github,
             the below inserts will need to be replaced with an actual user in the db dump file
            */
            // Add extra user with tool for testing user access
            runInsertStatement("insert into enduser(id, isAdmin, username) VALUES (2,true,'admin@admin.com');", new KeyedHandler<>("id"));
            runInsertStatement("insert into token(id, content, tokensource, userid, username) VALUES (5, '" + DUMMY_TOKEN_1
                    + "', 'dockstore', 2, 'admin@admin.com');", new KeyedHandler<>("id"));

            runInsertStatement(
                    "insert into tool(id, name, namespace, registry, path, validTrigger, ispublished, toolname) VALUES (9, 'test1', 'test_org', 'QUAY_IO', 'quay.io/test_org/test1', false, false,'');",
                    new KeyedHandler<>("id"));
            runInsertStatement("insert into user_entry(userid, entryid) VALUES (2, 9);", new KeyedHandler<>("entryid"));


            // need to increment past manually entered ids above
            runUpdateStatementConfidential("alter sequence container_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence tag_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence sourcefile_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence label_id_seq restart with 1000;");

        }

        private void runInsertDump(String sqlDumpPath) throws IOException {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(sqlDumpPath), "utf-8"));
            String line = null;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("INSERT")) {
                    runUpdateStatementConfidential(line);
                } else if (line.startsWith("SELECT")){
                    this.runSelectStatement(line, new ScalarHandler<>(), null);
                }
            }
            br.close();
        }

        public void clearDatabaseMakePrivate2() throws IOException {
            super.clearDatabase();

            runInsertDump(ResourceHelpers.resourceFilePath("db_confidential_dump_full_2.sql"));

            // need to increment past manually entered ids above
            runUpdateStatementConfidential("alter sequence container_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence tag_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence sourcefile_id_seq restart with 1000;");
            runUpdateStatementConfidential("alter sequence label_id_seq restart with 1000;");
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
     * Clears database state and known queues for confidential testing. For DockstoreTestUser
     * @throws IOException
         */
    public static void clearStateMakePrivate() throws IOException {
        final TestingPostgres postgres = getTestingPostgres();
        postgres.clearDatabaseMakePrivate();

    }

    /**
     * Clears database state and known queues for confidential testing. For DockstoreTestUser2
     * @throws IOException
     */
    public static void clearStateMakePrivate2() throws IOException {
        final TestingPostgres postgres = getTestingPostgres();
        postgres.clearDatabaseMakePrivate2();

    }

    public static TestingPostgres getTestingPostgres() {
        final File configFile = FileUtils.getFile("src", "test", "resources", "config");
        final HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        return new TestingPostgres(parseConfig);
    }
}
