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

import static io.dockstore.client.cli.BaseIT.BIOWORKFLOW;
import static io.dockstore.client.cli.BaseIT.getWebClient;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
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
    public void settingVersionMetadata() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(EXT.getTestSupport(), false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        final String installationId = "1179416";

        // Test creating new version
        client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);

        WorkflowVersion workflowVersion = getWorkflowVersion(client);
        assertTrue(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Should be set to true since there's no inaccessible URL in the JSON");

        testingPostgres.runUpdateStatement("update version_metadata set publicaccessibletestparameterfile = null");
        workflowVersion = getWorkflowVersion(client);
        Assertions.assertNull(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Database should've reverted it to null");

        // Test updating existing version
        client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        workflowVersion = getWorkflowVersion(client);
        assertTrue(workflowVersion.getVersionMetadata().isPublicAccessibleTestParameterFile(), "Should be set to true since there's no inaccessible URL in the JSON");
    }

    private Workflow getFoobar1Workflow(WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "versions");
    }

    private WorkflowVersion getWorkflowVersion(WorkflowsApi client) {
        Workflow workflow = getFoobar1Workflow(client);
        Optional<WorkflowVersion> first = workflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("0.1")).findFirst();
        return first.get();
    }

}
