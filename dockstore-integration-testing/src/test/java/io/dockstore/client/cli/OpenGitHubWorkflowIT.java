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

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.openapi.model.DescriptorType;
import io.swagger.client.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class OpenGitHubWorkflowIT extends BaseIT {


    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();


    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * Tests <a href="https://ucsc-cgl.atlassian.net/browse/SEAB-6717">...</a>.
     * @throws ApiException
     */
    @Test
    void testEmptyRepoRefresh() throws ApiException {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // refresh empty repository
        Workflow workflow = manualRegisterAndPublish(workflowApi, "dockstore-testing/empty-repo", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", false);

        // refresh targeted
        workflowApi.refresh1(workflow.getId(), false);
        // should not throw exception
    }

}
