package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.List;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.CloudInstancesApi;
import io.dockstore.openapi.client.model.CloudInstance;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class CloudInstanceIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false);
    }

    @Test
    public void cloudInstanceResourceTest() {
        ApiClient adminApiClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient memberApiClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        ApiClient anonymousApiClient = getAnonymousOpenAPIWebClient();
        CloudInstancesApi adminCloudInstancesApi = new CloudInstancesApi(adminApiClient);
        CloudInstancesApi memberCloudInstancesApi = new CloudInstancesApi(memberApiClient);
        CloudInstancesApi anonymousCloudInstancesApi = new CloudInstancesApi(anonymousApiClient);
        List<CloudInstance> adminCloudInstances = adminCloudInstancesApi.getCloudInstances();
        List<CloudInstance> memberCloudInstances = memberCloudInstancesApi.getCloudInstances();
        List<CloudInstance> anonymousCloudInstances = anonymousCloudInstancesApi.getCloudInstances();
        Assert.assertEquals(0, adminCloudInstances.size());
        Assert.assertEquals(0, memberCloudInstances.size());
        Assert.assertEquals(0, anonymousCloudInstances.size());
        CloudInstance newCloudInstance = new CloudInstance();
        // This should not do anything
        Long ignoredId = 9001L;
        newCloudInstance.setId(ignoredId);
        newCloudInstance.setPartner("potato");
        newCloudInstance.setUrl("www.google.ca");
        newCloudInstance.setSupportsFileImports(null);
        newCloudInstance.setSupportsHttpImports(null);
        newCloudInstance.setSupportedLanguages(new ArrayList<>());
        try {
            anonymousCloudInstancesApi.postCloudInstance(newCloudInstance);
            Assert.fail("Only admins can create a new cloud instance");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        try {
            memberCloudInstancesApi.postCloudInstance(newCloudInstance);
            Assert.fail("Only admins can create a new cloud instance");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, e.getCode());
        }
        adminCloudInstancesApi.postCloudInstance(newCloudInstance);
        adminCloudInstances = adminCloudInstancesApi.getCloudInstances();
        memberCloudInstances = memberCloudInstancesApi.getCloudInstances();
        anonymousCloudInstances = anonymousCloudInstancesApi.getCloudInstances();
        Assert.assertEquals(1, adminCloudInstances.size());
        Assert.assertEquals(1, memberCloudInstances.size());
        Assert.assertEquals(1, anonymousCloudInstances.size());
        Long actualId = anonymousCloudInstances.get(0).getId();
        Assert.assertNotEquals("Should have ignored the ID passed in", ignoredId, actualId);
        newCloudInstance.setPartner("onion");
        newCloudInstance.setUrl("www.google.com");
        adminCloudInstancesApi.postCloudInstance(newCloudInstance);
        adminCloudInstances = adminCloudInstancesApi.getCloudInstances();
        memberCloudInstances = memberCloudInstancesApi.getCloudInstances();
        anonymousCloudInstances = anonymousCloudInstancesApi.getCloudInstances();
        Assert.assertEquals(2, adminCloudInstances.size());
        Assert.assertEquals(2, memberCloudInstances.size());
        Assert.assertEquals(2, anonymousCloudInstances.size());
        try {
            anonymousCloudInstancesApi.deleteCloudInstance(actualId);
            Assert.fail("Only admins can create a new cloud instance");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        try {
            memberCloudInstancesApi.deleteCloudInstance(actualId);
            Assert.fail("Only admins can create a new cloud instance");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, e.getCode());
        }
        adminCloudInstancesApi.deleteCloudInstance(actualId);
        adminCloudInstances = adminCloudInstancesApi.getCloudInstances();
        memberCloudInstances = memberCloudInstancesApi.getCloudInstances();
        anonymousCloudInstances = anonymousCloudInstancesApi.getCloudInstances();
        Assert.assertEquals(1, adminCloudInstances.size());
        Assert.assertEquals(1, memberCloudInstances.size());
        Assert.assertEquals(1, anonymousCloudInstances.size());
        Assert.assertEquals("The potato cloud instance should be deleted, not onion", "onion", anonymousCloudInstances.get(0).getPartner());
    }
}
