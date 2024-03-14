package io.dockstore.webservice.resources;

import com.codahale.metrics.health.HealthCheck;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.io.IOException;
import java.util.Date;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchConsistencyHealthCheck extends HealthCheck  {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchConsistencyHealthCheck.class);
    private final SessionFactory sessionFactory;
    private BioWorkflowDAO bioWorkflowDAO;
    private ToolDAO toolDAO;
    private AppToolDAO appToolDAO;
    private NotebookDAO notebookDAO;

    public ElasticsearchConsistencyHealthCheck(SessionFactory sessionFactory,
        BioWorkflowDAO bioWorkflowDAO, ToolDAO toolDAO, AppToolDAO appToolDAO, NotebookDAO notebookDAO) {
        this.sessionFactory = sessionFactory;
        this.bioWorkflowDAO = bioWorkflowDAO;
        this.toolDAO = toolDAO;
        this.appToolDAO = appToolDAO;
        this.notebookDAO = notebookDAO;
    }

    private long countElasticsearchIndex(String index) throws IOException {
        RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
        CountRequest countRequest = new CountRequest(index);
        CountResponse countResponse = client.count(countRequest, RequestOptions.DEFAULT);
        // TODO Check for failures.
        return countResponse.getCount();
    }

    private long countAllPublishedNonCheckers(EntryDAO<?> dao) {
        return dao.countAllPublished(null, null, null, null, null, null, null, false);
    }

    @Override
    protected Result check() throws Exception {

        // Retrieve database entry counts.
        Session session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
        long dbWorkflowCount = countAllPublishedNonCheckers(bioWorkflowDAO);
        long dbToolCount = countAllPublishedNonCheckers(toolDAO) + countAllPublishedNonCheckers(appToolDAO);
        long dbNotebookCount = countAllPublishedNonCheckers(notebookDAO);
        session.close();

        // Retrieve Elasticsearch index counts.
        long esWorkflowCount = countElasticsearchIndex("workflows");
        long esToolCount = countElasticsearchIndex("tools");
        long esNotebookCount = countElasticsearchIndex("notebooks");

        // Compare and return the appropriate result.
        String counts = String.format("esWorkflowCount=%d, dbWorkflowCount=%d, esToolCount=%d, dbToolCount=%d, esNotebookCount=%d, dbNotebookCount=%d",
            esWorkflowCount,  dbWorkflowCount,
            esToolCount,  dbToolCount,
            esNotebookCount,  dbNotebookCount);
            
        if (esWorkflowCount == dbWorkflowCount
            && esToolCount == dbToolCount
            && esNotebookCount == dbNotebookCount) {
            LOG.info("Elasticsearch is consistent, " + counts);
            return Result.healthy();
        } else {
            LOG.error("Elasticsearch is not consistent, " + counts);
            return Result.unhealthy("Elasticsearch is not consistent");
        }
    }
}
