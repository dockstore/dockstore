package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.UsersApi;
import java.util.Arrays;
import java.util.List;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Category(ConfidentialTest.class)
public class UserResourceDockerRegistriesIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }
    @Test
    public void getUserDockerRegistriesTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualRegistries = usersApi.getUserDockerRegistries();
        List<String> expectedRegistries = Arrays.asList("quay.io", "gitlab.com");
        assertEquals(expectedRegistries, actualRegistries, "Should have the expected Docker registries");
    }

    @Test
    public void getDockerRegistryOrganizationTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualNamespaces = usersApi.getDockerRegistriesOrganization("quay.io");
        List<String> expectedNamespaces = Arrays.asList("dockstore", "dockstoretestuser");
        assertEquals(expectedNamespaces, actualNamespaces, "Should have the expected namespaces");
        try {
            usersApi.getDockerRegistriesOrganization("fakeDockerRegistry");
            fail("Should not be able to get organizations of an unrecognized Docker registry");
        } catch (ApiException e) {
            assertEquals("Unrecognized Docker registry", e.getMessage(), "Should tell user that their Docker registry is unrecognized");
        }
    }

    @Test
    public void getDockerRegistryOrganizationRepositoriesTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        List<String> actualRepositories = usersApi.getDockerRegistryOrganizationRepositories("quay.io", "dockstoretestuser");
        List<String> expectedRepositories = Arrays.asList("noautobuild", "nobuildsatall", "quayandbitbucket", "quayandbitbucketalternate", "quayandgithub", "quayandgithubalternate", "quayandgithubwdl", "quayandgitlab", "quayandgitlabalternate", "test_input_json");
        expectedRepositories.forEach(repository -> {
            assertTrue(actualRepositories.contains(repository), "Should have the expected repositories");
        });
    }
}
