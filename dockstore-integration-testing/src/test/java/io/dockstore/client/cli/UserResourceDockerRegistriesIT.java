package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.UsersApi;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

@Category(ConfidentialTest.class)
public class UserResourceDockerRegistriesIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }
    @Test
    public void getUserDockerRegistriesTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualRegistries = usersApi.getUserDockerRegistries();
        List<String> expectedRegistries = Arrays.asList("quay.io", "gitlab.com");
        Assert.assertEquals("Should have the expected Docker registries", expectedRegistries, actualRegistries);
    }

    @Test
    public void getDockerRegistryOrganizationTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualNamespaces = usersApi.getDockerRegistriesOrganization("quay.io");
        List<String> expectedNamespaces = Arrays.asList("dockstore", "dockstoretestuser");
        Assert.assertEquals("Should have the expected namespaces", expectedNamespaces, actualNamespaces);
        try {
            usersApi.getDockerRegistriesOrganization("fakeDockerRegistry");
            Assert.fail("Should not be able to get organizations of an unrecognized Docker registry");
        } catch (ApiException e) {
            Assert.assertEquals("Should tell user that their Docker registry is unrecognized", "Unrecognized Docker registry", e.getMessage());
        }
    }

    @Test
    public void getDockerRegistryOrganizationRepositoriesTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualRepositories = usersApi.getDockerRegistryOrganizationRepositories("quay.io", "dockstoretestuser");
        List<String> expectedRepositories = Arrays.asList("noautobuild", "nobuildsatall", "quayandbitbucket", "quayandbitbucketalternate", "quayandgithub", "quayandgithubalternate", "quayandgithubwdl", "quayandgitlab", "quayandgitlabalternate", "test_input_json");
        expectedRepositories.forEach(repository -> {
            Assert.assertTrue("Should have the expected repositories", actualRepositories.contains(repository));
        });
    }
}
