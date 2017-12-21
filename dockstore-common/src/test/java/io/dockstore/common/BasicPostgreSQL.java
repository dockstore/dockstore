/*
 *    Copyright 2017 OICR
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public class BasicPostgreSQL {

    protected static final Logger LOG = LoggerFactory.getLogger(BasicPostgreSQL.class);
    private static DataSource dataSource = null;

    public BasicPostgreSQL(INIConfiguration settings) {
        if (dataSource == null) {
            try {
                String nullConfigs = "";
                String host = settings.getString(Constants.POSTGRES_HOST);
                if (host == null) {
                    nullConfigs += "postgresHost ";
                }

                String user = settings.getString(Constants.POSTGRES_USERNAME);
                if (user == null) {
                    nullConfigs += "postgresUser ";
                }

                String pass = settings.getString(Constants.POSTGRES_PASSWORD);
                if (pass == null) {
                    nullConfigs += "postgresPass ";
                }

                String db = settings.getString(Constants.POSTGRES_DBNAME);
                if (db == null) {
                    nullConfigs += "postgresDBName ";
                }

                String maxConnections = settings.getString(Constants.POSTGRES_MAX_CONNECTIONS, "5");

                if (!nullConfigs.trim().isEmpty()) {
                    throw new NullPointerException(
                            "The following configuration values are null: " + nullConfigs + ". Please check your configuration file.");
                }

                Class.forName("org.postgresql.Driver");

                String url = "jdbc:postgresql://" + host + "/" + db;
                LOG.debug("PostgreSQL URL is: " + url);
                Properties props = new Properties();
                props.setProperty("user", user);
                props.setProperty("password", pass);
                // props.setProperty("ssl","true");
                props.setProperty("initialSize", "5");
                props.setProperty("maxActive", maxConnections);

                ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(url, props);
                PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
                poolableConnectionFactory.setValidationQuery("select count(*) from container;");
                ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory);
                poolableConnectionFactory.setPool(connectionPool);
                dataSource = new PoolingDataSource<>(connectionPool);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This clears the data base for testing and creates an admin user
     */
    public void clearDatabase() {
        runUpdateStatement("delete from user_entry;");
        runUpdateStatement("delete from endusergroup;");
        runUpdateStatement("delete from starred;");
        runUpdateStatement("delete from enduser;");
        runUpdateStatement("delete from token;");
        runUpdateStatement("delete from version_sourcefile;");
        runUpdateStatement("delete from sourcefile;");
        runUpdateStatement("delete from tool_tag;");
        runUpdateStatement("delete from tag;");
        runUpdateStatement("delete from workflow_workflowversion;");
        runUpdateStatement("delete from workflowversion;");
        runUpdateStatement("delete from entry_label;");
        runUpdateStatement("delete from label;");
        runUpdateStatement("delete from workflow;");
        runUpdateStatement("delete from tool;");
        runUpdateStatement("delete from usergroup;");
        runUpdateStatement("delete from databasechangelog;");
        runUpdateStatement("delete from databasechangeloglock;");
    }

    protected <T> T runSelectStatement(String query, ResultSetHandler<T> handler, Object... params) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            return run.query(query, handler, params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean runUpdateStatement(String query, Object... params) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            run.update(query, params);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
