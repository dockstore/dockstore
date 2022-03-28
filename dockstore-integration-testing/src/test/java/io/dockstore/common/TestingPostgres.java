/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.common;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

/**
 * @author gluu
 * @since 1.7.0
 */
public class TestingPostgres {

    private static final String SELECT_COUNT_FROM_EVENT_E_WHERE_E_TYPE =
        "select count(*) from event e where e.type = ";
    private Jdbi jdbi;

    public TestingPostgres(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        DataSourceFactory dataSourceFactory = support.getConfiguration().getDataSourceFactory();
        Environment environment = support.getEnvironment();
        JdbiFactory jdbiFactory = new JdbiFactory();
        jdbi = jdbiFactory.build(environment, dataSourceFactory, "postgresql");

    }

    public int runUpdateStatement(String query) {
        return jdbi.withHandle(handle -> handle.createUpdate(query).execute());
    }

    public <T> T runSelectStatement(String statement, Class<T> handler) {
        return jdbi.withHandle(handle -> {
            Query query = handle.select(statement);
            return query.mapTo(handler).findFirst().orElse(null);
        });
    }

    public <T> List<T> runSelectListStatement(String statement, Class<T> handler) {
        return jdbi.withHandle(handle -> {
            Query query = handle.select(statement);
            return query.mapTo(handler).list();
        });
    }

    public long getPublishEventCount() {
        return runSelectStatement(SELECT_COUNT_FROM_EVENT_E_WHERE_E_TYPE + "'PUBLISH_ENTRY'", Long.class);
    }

    public long getUnpublishEventCount() {
        return runSelectStatement(SELECT_COUNT_FROM_EVENT_E_WHERE_E_TYPE + "'UNPUBLISH_ENTRY'", Long.class);
    }

    public long getPublishEventCountForWorkflow(long workflowId) {
        return runSelectStatement(
            SELECT_COUNT_FROM_EVENT_E_WHERE_E_TYPE + "'PUBLISH_ENTRY' and workflowId = " + workflowId, Long.class);
    }

    public long getUnpublishEventCountForWorkflow(long workflowId) {
        return runSelectStatement(
            SELECT_COUNT_FROM_EVENT_E_WHERE_E_TYPE + "'UNPUBLISH_ENTRY' and workflowId = " + workflowId, Long.class);
    }
}
