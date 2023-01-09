package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.IOException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

class ElasticSearchHelperIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @Test
    void testDoMappingsExist() throws IOException {
        RestHighLevelClient esClient = ElasticSearchHelper.restHighLevelClient();
        assertTrue(ElasticSearchHelper.doMappingsExist(), "Should return true because mappings should exist upon startup");
        deleteIndex(ElasticListener.TOOLS_INDEX, esClient);
        assertFalse(ElasticSearchHelper.doMappingsExist(), "Should return false if one of the indices are missing its mapping");
        deleteIndex(ElasticListener.WORKFLOWS_INDEX, esClient);
        assertFalse(ElasticSearchHelper.doMappingsExist(), "Should return false if all indices are missing its mapping");
    }

    @Test
    void testAcquireAndReleaseLock() {
        assertTrue(ElasticSearchHelper.acquireLock());
        assertFalse(ElasticSearchHelper.acquireLock(), "Should not be able to acquire lock if the lock is being used");
        assertTrue(ElasticSearchHelper.releaseLock(), "Should be able to release lock successfully");
    }

    private void deleteIndex(String index, RestHighLevelClient esClient) throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
        esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }
}
