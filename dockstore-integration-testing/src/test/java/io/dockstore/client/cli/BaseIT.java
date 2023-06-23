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
package io.dockstore.client.cli;

import static io.dockstore.openapi.client.model.DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codahale.metrics.Gauge;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.CommonTestUtilities.TestUser;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Constants;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.auth.OAuth;
import io.dockstore.openapi.client.model.Repository;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.ModeEnum;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.resources.WorkflowSubClass;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/**
 * Base integration test class
 * A default configuration that cleans the database between tests and provides some basic methods
 */
@ExtendWith(TestStatus.class)
@ExtendWith(SystemStubsExtension.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
public class BaseIT {

    // This is obviously an admin
    public static final String ADMIN_USERNAME = "admin@admin.com";
    // This is also an admin
    public static final String USER_1_USERNAME = TestUser.TEST_USER1.dockstoreUserName;
    // This is also an admin
    public static final String USER_2_USERNAME = TestUser.TEST_USER2.dockstoreUserName;
    // This is also an admin
    public static final String USER_4_USERNAME = TestUser.TEST_USER4.dockstoreUserName;
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    protected static TestingPostgres testingPostgres;
    // This is not an admin
    public static final String OTHER_USERNAME = "OtherUser";

    public static final WorkflowSubClass SERVICE = WorkflowSubClass.SERVICE;
    public static final WorkflowSubClass BIOWORKFLOW = WorkflowSubClass.BIOWORKFLOW;
    public static final WorkflowSubClass APPTOOL = WorkflowSubClass.APPTOOL;

    @SuppressWarnings("checkstyle:ParameterNumber")
    static io.dockstore.openapi.client.model.DockstoreTool manualRegisterAndPublish(io.dockstore.openapi.client.api.ContainersApi containersApi, String namespace, String name, String toolName,
        String gitUrl, String cwlPath, String wdlPath, String dockerfilePath, io.dockstore.openapi.client.model.DockstoreTool.RegistryEnum registry, String gitReference,
        String versionName, boolean toPublish, boolean isPrivate, String email, String customDockerPath) {
        io.dockstore.openapi.client.model.DockstoreTool newTool = new io.dockstore.openapi.client.model.DockstoreTool();
        newTool.setNamespace(namespace);
        newTool.setName(name);
        newTool.setToolname(toolName);
        newTool.setDefaultCwlPath(cwlPath);
        newTool.setDefaultWdlPath(wdlPath);
        newTool.setDefaultDockerfilePath(dockerfilePath);
        newTool.setGitUrl(gitUrl);
        newTool.setRegistry(registry);
        newTool.setRegistryString(registry.getValue());
        newTool.setMode(MANUAL_IMAGE_PATH);
        newTool.setPrivateAccess(isPrivate);
        newTool.setToolMaintainerEmail(email);
        if (customDockerPath != null) {
            newTool.setRegistryString(customDockerPath);
        }

        if (!Registry.QUAY_IO.name().equals(registry.name())) {
            io.dockstore.openapi.client.model.Tag tag = new Tag();
            tag.setReference(gitReference);
            tag.setName(versionName);
            tag.setDockerfilePath(dockerfilePath);
            tag.setCwlPath(cwlPath);
            tag.setWdlPath(wdlPath);
            List<Tag> tags = new ArrayList<>();
            tags.add(tag);
            newTool.setWorkflowVersions(tags);
        }

        // Manually register
        io.dockstore.openapi.client.model.DockstoreTool tool = containersApi.registerManual(newTool);

        // Refresh
        tool = containersApi.refresh(tool.getId());

        // Publish
        if (toPublish) {
            tool = containersApi.publish(tool.getId(), CommonTestUtilities.createPublishRequest(true));
            assertTrue(tool.isIsPublished());
        }
        return tool;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    static io.dockstore.openapi.client.model.DockstoreTool manualRegisterAndPublish(io.dockstore.openapi.client.api.ContainersApi containersApi, String namespace, String name, String toolName,
        String gitUrl, String cwlPath, String wdlPath, String dockerfilePath, io.dockstore.openapi.client.model.DockstoreTool.RegistryEnum registry, String gitReference,
        String versionName, boolean toPublish) {
        return manualRegisterAndPublish(containersApi, namespace, name, toolName, gitUrl, cwlPath, wdlPath, dockerfilePath, registry,
            gitReference, versionName, toPublish, false, null, null);
    }

    /**
     * Manually register and publish a workflow with the given path and name
     *
     * @param workflowsApi
     * @param workflowPath
     * @param workflowName
     * @param descriptorType
     * @param sourceControl
     * @param descriptorPath
     * @param toPublish
     * @return Published workflow
     */
    static io.dockstore.openapi.client.model.Workflow manualRegisterAndPublish(io.dockstore.openapi.client.api.WorkflowsApi workflowsApi, String workflowPath, String workflowName, String descriptorType,
        SourceControl sourceControl, String descriptorPath, boolean toPublish) {
        // Manually register
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi
            .manualRegister(sourceControl.getFriendlyName().toLowerCase(), workflowPath, descriptorPath, workflowName, descriptorType,
                "/test.json");
        assertEquals(ModeEnum.STUB, workflow.getMode());

        // Refresh
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertEquals(ModeEnum.FULL, workflow.getMode());

        // Publish
        if (toPublish) {
            workflow = workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
            assertTrue(workflow.isIsPublished());
        }
        return workflow;
    }

    static void commonSmartRefreshTest(SourceControl sourceControl, String workflowPath, String versionOfInterest) {
        io.dockstore.openapi.client.ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(client);

        String correctDescriptorPath = "/Dockstore.cwl";
        String incorrectDescriptorPath = "/Dockstore2.cwl";

        String fullPath = sourceControl.toString() + "/" + workflowPath;

        // Add workflow
        workflowsApi.manualRegister(sourceControl.name(), workflowPath, correctDescriptorPath, "",
                DescriptorLanguage.CWL.getShortName(), "");

        // Smart refresh individual that is valid (should add versions that doesn't exist)
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.getWorkflowByPath(fullPath, io.dockstore.openapi.client.model.WorkflowSubClass.BIOWORKFLOW, "");
        workflow = workflowsApi.refresh1(workflow.getId(), false);

        // All versions should be synced
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertTrue(workflowVersion.isSynced()));

        // When the commit ID is null, a refresh should occur
        io.dockstore.openapi.client.model.WorkflowVersion oldVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testingPostgres.runUpdateStatement("update workflowversion set commitid = NULL where name = '" + versionOfInterest + "'");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        io.dockstore.openapi.client.model.WorkflowVersion updatedVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertNotNull(updatedVersion.getCommitID());
        assertNotEquals(oldVersion.getDbUpdateDate(), updatedVersion.getDbUpdateDate(), versionOfInterest + " version should be updated (different dbupdatetime)");

        // When the commit ID is different, a refresh should occur
        oldVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testingPostgres.runUpdateStatement("update workflowversion set commitid = 'dj90jd9jd230d3j9' where name = '" + versionOfInterest + "'");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        updatedVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertNotNull(updatedVersion.getCommitID());
        assertNotEquals(oldVersion.getDbUpdateDate(), updatedVersion.getDbUpdateDate(), versionOfInterest + " version should be updated (different dbupdatetime)");

        // Updating the workflow should make the version not synced, a refresh should refresh all versions
        workflow.setWorkflowPath(incorrectDescriptorPath);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertFalse(workflowVersion.isSynced()));
        workflow = workflowsApi.refresh1(workflow.getId(), false);

        // All versions should be synced and updated
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertTrue(workflowVersion.isSynced()));
        workflow.getWorkflowVersions().forEach(workflowVersion -> Objects.equals(workflowVersion.getWorkflowPath(), incorrectDescriptorPath));

        // Update the version to have the correct path
        io.dockstore.openapi.client.model.WorkflowVersion testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testBothVersion.setWorkflowPath(correctDescriptorPath);
        List<WorkflowVersion> versions = new ArrayList<>();
        versions.add(testBothVersion);
        workflowsApi.updateWorkflowVersion(workflow.getId(), versions);

        // Refresh should only update the version that is not synced
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertFalse(testBothVersion.isSynced(), "Version should not be synced");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertTrue(testBothVersion.isSynced(), "Version should now be synced");
        assertEquals(correctDescriptorPath, testBothVersion.getWorkflowPath(), "Workflow version path should be set");
    }

    static void refreshByOrganizationReplacement(io.dockstore.openapi.client.api.WorkflowsApi workflowApi, io.dockstore.openapi.client.ApiClient openAPIWebClient) {
        io.dockstore.openapi.client.api.UsersApi openUsersApi = new io.dockstore.openapi.client.api.UsersApi(openAPIWebClient);
        for (SourceControl control : SourceControl.values()) {
            List<String> userOrganizations = openUsersApi.getUserOrganizations(control.name());
            for (String org : userOrganizations) {
                List<Repository> userOrganizationRepositories = openUsersApi.getUserOrganizationRepositories(control.name(), org);
                for (Repository repo : userOrganizationRepositories) {
                    workflowApi.manualRegister(control.name(), repo.getPath(), "/Dockstore.cwl", "",
                        DescriptorLanguage.CWL.getShortName(), "");
                }
            }
        }
    }

    static io.dockstore.openapi.client.model.Workflow registerGatkSvWorkflow(io.dockstore.openapi.client.api.WorkflowsApi ownerWorkflowApi) {
        // Register and refresh workflow
        io.dockstore.openapi.client.model.Workflow workflow = ownerWorkflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "dockstore-testing/gatk-sv-clinical", "/GATKSVPipelineClinical.wdl",
            "test", "wdl", "/test.json");
        return ownerWorkflowApi.refresh1(workflow.getId(), false);
    }

    static io.dockstore.openapi.client.model.WorkflowVersion snapshotWorkflowVersion(WorkflowsApi workflowsApi, Workflow workflow, String versionName) {
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals(versionName)).findFirst().get();
        version.setFrozen(true);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
        workflow = workflowsApi.getWorkflow(workflow.getId(), "images");
        return workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals(versionName)).findFirst().get();
    }



    final String curatorUsername = "curator@curator.com";

    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    public static void assertNoMetricsLeaks(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws InterruptedException {
        SortedMap<String, Gauge> gauges = support.getEnvironment().metrics().getGauges();
        int active = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        int waiting = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.waiting").getValue();
        if (active != 0 || waiting != 0) {
            // Waiting 10 seconds to see if active connection disappears
            TimeUnit.SECONDS.sleep(10);
            active = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
            waiting = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.waiting").getValue();
            assertEquals(0, active, "There should be no active connections");
            assertEquals(0, waiting, "There should be no waiting connections");
        }
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    protected static io.dockstore.openapi.client.ApiClient getOpenAPIWebClient(String username, TestingPostgres testingPostgresParameter) {
        return CommonTestUtilities.getWebClient(true, username, testingPostgresParameter);
    }

    /**
     * the following were migrated from SwaggerClientIT and can be eventually merged. Note different config file used
     */
    protected static io.dockstore.openapi.client.ApiClient getWebClient(String username, TestingPostgres testingPostgresParameter) {
        return CommonTestUtilities.getWebClient(true, username, testingPostgresParameter);
    }

    protected static ApiClient getWebClient() {
        return getWebClient(true, false);
    }

    protected static ApiClient getWebClient(boolean correctUser, boolean admin) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        OAuth bearer = (OAuth)client.getAuthentication("BEARER");
        bearer.setAccessToken((correctUser ? parseConfig.getString(admin ? Constants.WEBSERVICE_TOKEN_USER_1 : Constants.WEBSERVICE_TOKEN_USER_2)
            : "foobar"));
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    static ApiClient getAdminWebClient() {
        return getWebClient(true, true);
    }

    protected static ApiClient getAnonymousWebClient() {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    protected static io.dockstore.openapi.client.ApiClient getAnonymousOpenAPIWebClient() {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        io.dockstore.openapi.client.ApiClient client = new io.dockstore.openapi.client.ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    @AfterEach
    public void after() throws InterruptedException {
        assertNoMetricsLeaks(SUPPORT);
    }

    @BeforeEach
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    public static class TestStatus implements TestWatcher {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.printf("Test successful: %s%n", context.getTestMethod().get());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.printf("Test failed: %s%n", context.getTestMethod().get());
        }
    }
}
