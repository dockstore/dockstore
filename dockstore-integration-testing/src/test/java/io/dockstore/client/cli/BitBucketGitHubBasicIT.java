/*
 *    Copyright 2022 OICR and UCSC
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

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Registry;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Basic confidential integration tests, focusing on publishing/unpublishing both automatic and manually added tools
 * This is important as it tests the web service with real data instead of dummy data, using actual services like Github and Quay
 *
 * @author aduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(BitBucketTest.NAME)
class BitBucketGitHubBasicIT extends BaseIT {
    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres, true);
    }

    /*
     * General-ish tests
     */

    /**
     * Tests that refresh all works, also that refreshing without a quay.io token should not destroy tools
     */
    @Test
    void testRefresh() {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);

        final long startToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        // should have 0 tools to start with
        usersApi.refreshToolsByOrganization((long)1, "DockstoreTestUser", "quayandbitbucket");
        // should have a certain number of tools based on github contents
        final long secondToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        assertTrue(startToolCount <= secondToolCount && secondToolCount > 1);

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'quay.io'");

        // refresh
        try {
            usersApi.refreshToolsByOrganization((long)1, "DockstoreTestUser", "quayandbitbucket");
            fail("Refresh should fail");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Please add a Quay.io token"), "Should see error message since user has Quay tools but no Quay token.");
            // should not delete tools
            final long thirdToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
            assertEquals(secondToolCount, thirdToolCount, "there should be no change in count of tools");
        }
    }


    /**
     * Check that refreshing an existing tool with a different tool name will not throw an error
     */
    @Test
    void testRefreshCorrectTool() {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);
        DockstoreTool tool = toolsApi.getContainerByToolPath("quay.io/dockstoretestuser/quayandbitbucket", "");
        toolsApi.refresh(tool.getId());

        // Register BitBucket tool
        DockstoreTool bitbucketTool = manualRegisterAndPublish(toolsApi, "dockstoretestuser", "dockerhubandbitbucket", "regular",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "/Dockstore.cwl", "/Dockstore.wdl", "/Dockerfile",
            DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);
        bitbucketTool = toolsApi.refresh(bitbucketTool.getId());
        assertTrue(bitbucketTool.isIsPublished());

        // Register GitHub tool
        DockstoreTool githubTool = manualRegisterAndPublish(toolsApi, "dockstoretestuser", "dockerhubandgithub", "regular",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "/Dockstore.cwl", "/Dockstore.wdl", "/Dockerfile",
            DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);
        githubTool = toolsApi.refresh(githubTool.getId());
        assertTrue(githubTool.isIsPublished());
    }


    /*
     * Tests for creating tools from various image registries and git registries
     */

    /**
     * Tests auto registration of tools
     */
    @Test
    void testAutoRegistration() {
        autoRegistrationHelper(Registry.QUAY_IO, "github.com", 5);
        autoRegistrationHelper(Registry.QUAY_IO, "bitbucket.org", 2);
        autoRegistrationHelper(Registry.QUAY_IO, "gitlab.com", 2);
    }

    private void autoRegistrationHelper(Registry imageRegistry, String gitRegistry, int expectedToolCount) {
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + imageRegistry.getDockerPath() + "' and giturl like 'git@" + gitRegistry + "%'",
            long.class);
        assertEquals(expectedToolCount, count, "there should be " + expectedToolCount + " registered from " + imageRegistry + " and " + gitRegistry + ", there are " + count);
    }

    /**
     * Tests publishing tools with non-standard structure
     */
    @Test
    void testPublishAlternateStructure() {
        publishAlternateStructureHelper("quay.io/dockstoretestuser/quayandgithubalternate");
        publishAlternateStructureHelper("quay.io/dockstoretestuser/quayandbitbucketalternate");
        publishAlternateStructureHelper("quay.io/dockstoretestuser/quayandgitlabalternate");
    }

    private void publishAlternateStructureHelper(String toolPath) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);
        DockstoreTool tool = toolsApi.getContainerByToolPath(toolPath, "");
        try {
            toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(true));
            fail("Should not be able to publish");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * Tests publishing tools with normal structure
     */
    @Test
    void testPublishAndUnpublishTool() {
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandgithub");
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandbitbucket");
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandgitlab");
    }

    @Test
    void testPublishToolEvents() {
        assertEquals(0, testingPostgres.getPublishEventCount(), "There should be no publish events");
        assertEquals(0, testingPostgres.getUnpublishEventCount(), "There should be no unpublish events");
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandgithub");
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandbitbucket");
        publishAndUnpublishToolHelper("quay.io/dockstoretestuser/quayandgitlab");
        final long pubEventsCount = testingPostgres.getPublishEventCount();
        final long unpubEventsCount = testingPostgres.getUnpublishEventCount();
        assertEquals(3, pubEventsCount, "There should be 3 publish events");
        assertEquals(3, unpubEventsCount, "There should be 3 unpublish events");
    }

    private void publishAndUnpublishToolHelper(String toolPath) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);
        DockstoreTool tool = toolsApi.getContainerByToolPath(toolPath, "");

        // Publish
        tool = toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(true));

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = '" + toolPath.split("/")[2] + "' and ispublished='t'", long.class);
        assertEquals(1, count, "there should be 1 registered");

        // Unpublish
        tool = toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(false));

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = '" + toolPath.split("/")[2] + "' and ispublished='t'", long.class);
        assertEquals(0, count2, "there should be 0 registered");
    }

    /**
     * Tests publishing tools with non-standard structure
     */
    @Test
    void testPublishAndUnpublishAlternateStructure() {
        publishAndUnpublishAlternateStructureHelper("dockstoretestuser", "quayandgithubalternate",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git");
        publishAndUnpublishAlternateStructureHelper("dockstoretestuser", "quayandbitbucketalternate",
            "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git");
        publishAndUnpublishAlternateStructureHelper("dockstoretestuser", "quayandgitlabalternate",
            "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git");
    }

    private void publishAndUnpublishAlternateStructureHelper(String namespace, String name, String gitUrl) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Manual publish
        DockstoreTool tool = manualRegisterAndPublish(toolsApi, namespace, name, "alternate", gitUrl, "/testDir/Dockstore.cwl",
            "/testDir/Dockstore.wdl", "/testDir/Dockerfile", DockstoreTool.RegistryEnum.QUAY_IO, "master", "latest", true);

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries, there are " + count);

        // Unpublish
        tool = toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(false));
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries, there are " + count2);
    }

    /**
     * Tests registering a tool that already exists
     */
    @Test
    void testManuallyRegisterDuplicate() {
        manuallyRegisterDuplicateHelper("dockstoretestuser", "quayandgithub", "git@github.com:DockstoreTestUser/dockstore-whalesay.git");
        manuallyRegisterDuplicateHelper("dockstoretestuser", "quayandbitbucket",
            "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git");
        manuallyRegisterDuplicateHelper("dockstoretestuser", "quayandgitlab", "git@gitlab.com:DockstoreTestUser/dockstore-whalesay.git");
    }

    private void manuallyRegisterDuplicateHelper(String namespace, String name, String gitUrl) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Manual publish
        try {
            DockstoreTool tool = manualRegisterAndPublish(toolsApi, namespace, name, "", gitUrl, "/Dockstore.cwl", "/Dockstore.wdl",
                "/Dockerfile", DockstoreTool.RegistryEnum.QUAY_IO, "master", "latest", true);
            fail("Should not be able to register");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }


    /**
     * Tests the manual registration of a standard workflow
     */
    @Test
    void testManualRegistration() {
        manualRegistrationHelper("dockstoretestuser", "dockerhubandgithub", "git@github.com:DockstoreTestUser/dockstore-whalesay.git");
        manualRegistrationHelper("dockstoretestuser", "dockerhubandbitbucket",
            "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git");
        manualRegistrationHelper("dockstoretestuser", "dockerhubandgitlab", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git");
    }

    private void manualRegistrationHelper(String namespace, String name, String gitUrl) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Manual publish
        DockstoreTool tool = manualRegisterAndPublish(toolsApi, namespace, name, "regular", gitUrl, "/Dockstore.cwl", "/Dockstore.wdl",
            "/Dockerfile", DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries");

        // Unpublish
        toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(false));
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries");
    }

    /**
     * Tests the manual registration of a non-standard workflow
     */
    @Test
    void testManualRegistrationAlternativeStructure() {
        manualRegistrationAlternativeStructureHelper("dockstoretestuser", "dockerhubandgithub",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git");
        manualRegistrationAlternativeStructureHelper("dockstoretestuser", "dockerhubandbitbucket",
            "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git");
        manualRegistrationAlternativeStructureHelper("dockstoretestuser", "dockerhubandgitlab",
            "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git");
    }

    private void manualRegistrationAlternativeStructureHelper(String namespace, String name, String gitUrl) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Manual publish
        DockstoreTool tool = manualRegisterAndPublish(toolsApi, namespace, name, "alternate", gitUrl, "/testDir/Dockstore.cwl",
            "/testDir/Dockstore.wdl", "/testDir/Dockerfile", DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entries");

        // Unpublish
        toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(false));
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        assertEquals(0, count2, "there should be 0 entries");
    }

    /**
     * Tests the manual registration of an existing workflow
     */
    @Test
    void testManuallyRegisteringDuplicates() {
        manuallyRegisteringDuplicatesHelper("dockstoretestuser", "dockerhubandgithub",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git");
        manuallyRegisteringDuplicatesHelper("dockstoretestuser", "dockerhubandbitbucket",
            "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git");
        manuallyRegisteringDuplicatesHelper("dockstoretestuser", "dockerhubandgitlab",
            "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git");

    }

    private void manuallyRegisteringDuplicatesHelper(String namespace, String name, String gitUrl) {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Manual publish
        DockstoreTool tool = manualRegisterAndPublish(toolsApi, namespace, name, "regular", gitUrl, "/Dockstore.cwl", "/Dockstore.wdl",
            "/Dockerfile", DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        assertEquals(1, count, "there should be 1 entry");

        DockstoreTool duplicateTool = manualRegisterAndPublish(toolsApi, namespace, name, "regular2", gitUrl, "/Dockstore.cwl",
            "/Dockstore.wdl", "/Dockerfile", DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);

        // Unpublish the duplicate entry
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        assertEquals(2, count2, "there should be 2 entries");
        toolsApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(false));

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        assertEquals(1, count3, "there should be 1 entry");

        toolsApi.publish(duplicateTool.getId(), CommonTestUtilities.createPublishRequest(false));
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        assertEquals(0, count4, "there should be 0 entries");
    }


    /**
     * This tests that you can refresh user data by refreshing a tool
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    void testRefreshingUserMetadata() {
        ApiClient client = getWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);

        // Refresh a tool
        DockstoreTool tool = toolsApi.getContainerByToolPath("quay.io/dockstoretestuser/quayandbitbucket", "");
        toolsApi.refresh(tool.getId());

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        //final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals(1, count, "One user should have this info now, there are " + count);
    }

}
