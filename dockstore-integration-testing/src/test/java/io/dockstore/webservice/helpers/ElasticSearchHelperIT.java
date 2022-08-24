package io.dockstore.webservice.helpers;

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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class ElasticSearchHelperIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeClass
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @Test
    public void testDoMappingsExist() throws IOException {
        RestHighLevelClient esClient = ElasticSearchHelper.restHighLevelClient();
        Assert.assertTrue("Should return true because mappings should exist upon startup", ElasticSearchHelper.doMappingsExist());
        deleteIndex(ElasticListener.TOOLS_INDEX, esClient);
        Assert.assertFalse("Should return false if one of the indices are missing its mapping", ElasticSearchHelper.doMappingsExist());
        deleteIndex(ElasticListener.WORKFLOWS_INDEX, esClient);
        Assert.assertFalse("Should return false if all indices are missing its mapping", ElasticSearchHelper.doMappingsExist());
    }

    @Test
    public void testAcquireAndReleaseLock() {
        Assert.assertTrue(ElasticSearchHelper.acquireLock());
        Assert.assertFalse("Should not be able to acquire lock if the lock is being used", ElasticSearchHelper.acquireLock());
        Assert.assertTrue("Should be able to release lock successfully", ElasticSearchHelper.releaseLock());
    }

    private void deleteIndex(String index, RestHighLevelClient esClient) throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
        esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }
}
