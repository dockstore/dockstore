/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.client.cli;

import static io.dockstore.client.cli.BaseIT.getOpenAPIWebClient;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag(ConfidentialTest.NAME)
@ExtendWith(DropwizardExtensionsSupport.class)
public class CheckUrlHelperFullIT {

    public static String fakeCheckUrlLambdaBaseURL = "http://fakecheckurllambdabaseurl:3000";

    private static final DropwizardAppExtension EXT = new DropwizardAppExtension<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH,
        ConfigOverride.config("checkUrlLambdaUrl", fakeCheckUrlLambdaBaseURL),
        ConfigOverride.config("database.properties.hibernate.hbm2ddl.auto", "create"));


    protected static TestingPostgres testingPostgres;
    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";

    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(EXT.getTestSupport(), true);
        testingPostgres = new TestingPostgres(EXT.getTestSupport());
    }

    /**
     * If the check URL code was not running, it would default to null This tests that the check URL code was called and was able to set the version metadata Hoverfly is not used because the test
     * parameter file had no URL to check
     */
    @Test
    void settingVersionMetadata() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(EXT.getTestSupport(), false, testingPostgres);
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        final String installationId = "1179416";

        // Test creating new version
        final String dockstoreTestUser2 = "DockstoreTestUser2";
        final String gitReference = "refs/tags/0.1";
        client.handleGitHubRelease(gitReference, installationId, workflowRepo, dockstoreTestUser2);

        WorkflowVersion workflowVersion = getWorkflowVersion(client);
        assertFalse(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Should be set to true since there's no inaccessible URL in the JSON");

        testingPostgres.runUpdateStatement("update version_metadata set publicaccessibletestparameterfile = null");
        workflowVersion = getWorkflowVersion(client);
        Assertions.assertNull(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Database should've reverted it to null");

        // Test updating existing version
        client.handleGitHubRelease(gitReference, installationId, workflowRepo, dockstoreTestUser2);
        workflowVersion = getWorkflowVersion(client);
        assertFalse(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Should be set to false since the workflow has a file input parameter, and the JSON has no files");
    }

    /**
     * We don't have the checkurl lambda running in our integration tests, so the tests can only verify
     * code up until the lambda invocation. In this case, the hosted workflow has no file input parameters,
     * so the lambda doesn't need to get invoked, and the version should be marked open.
     */
    @Test
    void openDataCheckedForHostedWorkflow() {
        CommonTestUtilities.cleanStatePrivate2(EXT.getTestSupport(), false, testingPostgres);
        final ApiClient
            webClient = CommonTestUtilities.getOpenAPIWebClient(true, BasicIT.USER_2_USERNAME, testingPostgres);
        final HostedApi hostedApi = new HostedApi(webClient);
        final io.dockstore.openapi.client.api.WorkflowsApi
            workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        final io.dockstore.openapi.client.model.Workflow hostedWorkflow =
            CommonTestUtilities.createHostedWorkflowWithVersion(hostedApi);
        final io.dockstore.openapi.client.model.WorkflowVersion
            workflowVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).get(0);
        assertTrue(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Should be public because the descriptor has no parameters at all");
    }

    private Workflow getFoobar1Workflow(WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
    }

    private WorkflowVersion getWorkflowVersion(WorkflowsApi client) {
        Workflow workflow = getFoobar1Workflow(client);
        Optional<WorkflowVersion> first = workflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("0.1")).findFirst();
        return first.get();
    }

}
