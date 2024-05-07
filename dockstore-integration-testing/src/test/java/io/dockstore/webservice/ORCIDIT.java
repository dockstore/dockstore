/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.HoverflyTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.OrcidAuthorInformation;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(HoverflyTest.NAME)
public class ORCIDIT extends BaseIT {
    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * This test relies on Hoverfly to simulate responses from the ORCID API.
     * In the simulation, the responses are crafted for an ORCID author with ID 0000-0002-6130-1021.
     * ORCID authors with other IDs are considered "not found" by the simulation.
     */
    @Test
    void testGetWorkflowVersionOrcidAuthors() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        ApiClient anonymousWebClient = getAnonymousOpenAPIWebClient();
        WorkflowsApi anonymousWorkflowsApi = new WorkflowsApi(anonymousWebClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar");

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/main", USER_2_USERNAME);
        // WDL workflow
        Workflow workflow = workflowsApi.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
        try (Hoverfly hoverfly = new Hoverfly(HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ORCID_SIMULATION_SOURCE);
            List<OrcidAuthorInformation> orcidAuthorInfo = workflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
            assertEquals(1, orcidAuthorInfo.size()); // There's 1 OrcidAuthorInfo instead of 2 because only 1 ORCID ID from the version exists on ORCID

            // Publish workflow
            PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
            workflowsApi.publish1(workflow.getId(), publishRequest);

            // Check that an unauthenticated user can get the workflow version ORCID authors of a published workflow
            anonymousWorkflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
            assertEquals(1, orcidAuthorInfo.size());
        }
    }
}
