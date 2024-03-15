/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.resources;

import com.codahale.metrics.health.HealthCheck;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.hibernate.UnitOfWork;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchConsistencyHealthCheck extends HealthCheck  {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchConsistencyHealthCheck.class);
    private ToolDAO toolDAO;
    private BioWorkflowDAO bioWorkflowDAO;
    private AppToolDAO appToolDAO;
    private NotebookDAO notebookDAO;

    public ElasticsearchConsistencyHealthCheck(ToolDAO toolDAO, BioWorkflowDAO bioWorkflowDAO, AppToolDAO appToolDAO, NotebookDAO notebookDAO) {
        this.toolDAO = toolDAO;
        this.bioWorkflowDAO = bioWorkflowDAO;
        this.appToolDAO = appToolDAO;
        this.notebookDAO = notebookDAO;
    }

    private long countElasticsearchDocuments(String index) throws IOException {
        RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
        CountRequest countRequest = new CountRequest(index);
        CountResponse countResponse = client.count(countRequest, RequestOptions.DEFAULT);
        if (countResponse.status().getStatus() != HttpStatus.SC_OK) {
            throw new RuntimeException("Non-OK response to Elasticsearch request");
        }
        return countResponse.getCount();
    }

    private long countAllPublishedNonCheckers(EntryDAO<?> dao) {
        return dao.countAllPublished(null, null, null, null, null, null, null, false);
    }

    @UnitOfWork
    @Override
    protected Result check() throws Exception {

        // Retrieve database entry counts.
        long dbWorkflowCount = countAllPublishedNonCheckers(bioWorkflowDAO);
        long dbToolCount = countAllPublishedNonCheckers(toolDAO) + countAllPublishedNonCheckers(appToolDAO);
        long dbNotebookCount = countAllPublishedNonCheckers(notebookDAO);

        // Retrieve Elasticsearch document counts.
        long esWorkflowCount = countElasticsearchDocuments("workflows");
        long esToolCount = countElasticsearchDocuments("tools");
        long esNotebookCount = countElasticsearchDocuments("notebooks");

        // Return the appropriate result.
        if (esWorkflowCount == dbWorkflowCount
            && esToolCount == dbToolCount
            && esNotebookCount == dbNotebookCount) {
            return Result.healthy();
        } else {
            String counts = String.format("esWorkflowCount=%d, dbWorkflowCount=%d, esToolCount=%d, dbToolCount=%d, esNotebookCount=%d, dbNotebookCount=%d",
                esWorkflowCount,  dbWorkflowCount,
                esToolCount,  dbToolCount,
                esNotebookCount,  dbNotebookCount);
            return Result.unhealthy("Elasticsearch is not consistent with database: " + counts);
        }
    }
}
