/*
 *    Copyright 2018 OICR
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.CommonTestUtilities.TestUser;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
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
@Tag(BitBucketTest.NAME)
class BitBucketExtendedNextflowIT extends BaseIT {

    public static final String DOCKSTORE_TEST_USER_4 = TestUser.TEST_USER4.dockstoreUserName;
    // bitbucket workflow
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW =
        SourceControl.BITBUCKET + "/" + DOCKSTORE_TEST_USER_4 + "/ampa-nf";
    // workflow with binaries in bin directory
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW =
        SourceControl.BITBUCKET + "/" + DOCKSTORE_TEST_USER_4 + "/kallisto-nf";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use fileDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate(SUPPORT, false, testingPostgres, true, TestUser.TEST_USER4);
    }

    @AfterEach
    public void preserveBitBucketTokens() {
        CommonTestUtilities.cacheBitbucketTokens(SUPPORT);
    }


    @Test
    void testBitbucketNextflowWorkflow() {
        final ApiClient webClient = getWebClient(USER_4_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowsApi =
            new io.dockstore.openapi.client.api.WorkflowsApi(
                getOpenAPIWebClient(USER_4_USERNAME, testingPostgres));
        // get workflow stubs
        Workflow workflow = workflowApi.manualRegister(SourceControl.BITBUCKET.name(), DOCKSTORE_TEST_USER_4 + "/ampa-nf", "/nextflow.config", "",
                DescriptorLanguage.NEXTFLOW.getShortName(), "/foo.json");
        workflowApi.refresh(workflow.getId(), false);

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, BIOWORKFLOW, "versions");
        // need to set paths properly
        workflowByPathBitbucket.setWorkflowPath("/nextflow.config");
        workflowByPathBitbucket.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathBitbucket.getId(), workflowByPathBitbucket);
        workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, BIOWORKFLOW, "versions");
        final Workflow bitbucketWorkflow = workflowApi.refresh(workflowByPathBitbucket.getId(), false);
        Workflow byPathWorkflow = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, BIOWORKFLOW, "versions");
        // There are 3 versions: master, v1.0, and v2.0
        // master and v2.0 has a nextflow.config file that has description and author, v1.0 does not
        // v1.0 will pull description from README instead but the others will use nextflow.config. v1.0 also does not have a valid descriptor (or any sourcefiles)
        testWorkflowVersionMetadata(bitbucketWorkflow, openApiWorkflowsApi);
        testWorkflowVersionMetadata(byPathWorkflow, openApiWorkflowsApi);
        // Purposely mess up the metadata to test if it can be updated through refresh
        testingPostgres.runUpdateStatement("update author set email='bad_potato'");
        testingPostgres.runUpdateStatement("update author set name='bad_potato'");
        testingPostgres.runUpdateStatement("update version_metadata set description='bad_potato'");
        final Workflow refreshedBitbucketWorkflow = workflowApi.refresh(workflowByPathBitbucket.getId(), true);
        byPathWorkflow = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW, BIOWORKFLOW, "versions");
        // This tests if it can fix outdated metadata
        testWorkflowVersionMetadata(refreshedBitbucketWorkflow, openApiWorkflowsApi);
        testWorkflowVersionMetadata(byPathWorkflow, openApiWorkflowsApi);
        List<io.dockstore.webservice.core.SourceFile> sourceFileList = fileDAO.findSourceFilesByVersion(bitbucketWorkflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("v2.0")).findFirst().get().getId());
        assertEquals(4, sourceFileList.size());
    }

    /**
     * This tests the DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW metadata is correct after a
     * refresh
     *
     * @param workflow     The DOCKSTORE_TEST_USER_NEXTFLOW_BITBUCKET_WORKFLOW workflow
     * @param workflowsApi
     */
    private void testWorkflowVersionMetadata(Workflow workflow,
        final io.dockstore.openapi.client.api.WorkflowsApi workflowsApi) {

        final String partialReadmeDescription = "AMPA-NF is a pipeline for assessing the antimicrobial domains of proteins,";
        final String descriptorDescription = "Fast automated prediction of protein antimicrobial regions";
        final String versionWithReadmeDescription = "v1.0";

        assertEquals(descriptorDescription, workflow.getDescription());
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.getName().equals(versionWithReadmeDescription)));
        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            if (workflowVersion.getName().equals(versionWithReadmeDescription)) {
                assertTrue(workflowsApi.getWorkflowVersionDescription(workflow.getId(), workflowVersion.getId()).contains(partialReadmeDescription));
                assertNull(workflowVersion.getAuthor());
                assertNull(workflowVersion.getEmail());
            } else {
                assertNotNull(workflowsApi.getWorkflowVersionDescription(workflow.getId(), workflowVersion.getId()), descriptorDescription);
                assertEquals("test.user@test.com", workflowVersion.getAuthor());
                assertNull(workflowVersion.getEmail());
            }
        });
    }


    @Test
    void testBitbucketBinaryWorkflow() {
        final ApiClient webClient = getWebClient(USER_4_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        // get workflow stubs

        Workflow workflow = workflowApi
                .manualRegister(SourceControl.BITBUCKET.name(), DOCKSTORE_TEST_USER_4 + "/kallisto-nf", "/nextflow.config", "",
                        DescriptorLanguage.NEXTFLOW.getShortName(), "/foo.json");
        workflowApi.refresh(workflow.getId(), false);

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW, BIOWORKFLOW, null);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_BINARY_WORKFLOW, BIOWORKFLOW, null);
        final Workflow bitbucketWorkflow = workflowApi.refresh(workflowByPathGithub.getId(), false);
        assertTrue(bitbucketWorkflow.getDescription().contains("A Nextflow implementation of Kallisto & Sleuth RNA-Seq Tools"), "Should have gotten the description from README");
        List<SourceFile> sourceFileList = fileDAO.findSourceFilesByVersion(bitbucketWorkflow.getWorkflowVersions().stream().filter(version -> version.getName().equals("v1.0")).findFirst().get().getId());
        assertEquals(6, sourceFileList.size());
        // two of the files should essentially be blanked
        assertEquals(2, sourceFileList.stream()
            .filter(file -> file.getContent().split("\n").length == 1 && file.getContent().contains("Dockstore does not")).count(), "two files have our one-line warning");
    }

}
