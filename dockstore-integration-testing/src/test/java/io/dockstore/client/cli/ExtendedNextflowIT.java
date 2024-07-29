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

import static io.dockstore.common.RepositoryConstants.DockstoreTesting.WORKFLOW_NEXTFLOW_DOCKSTORE_YML;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
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
class ExtendedNextflowIT extends BaseIT {

    // workflow with a bin directory
    private static final String DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW = SourceControl.GITHUB + "/DockstoreTestUser/ampa-nf";
    private static final String DOCKSTORE_TESTING_NEXTFLOW_WORKFLOW = SourceControl.GITHUB + "/" + WORKFLOW_NEXTFLOW_DOCKSTORE_YML;

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

    @Test
    void testNextflowSecondaryFiles() {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/ampa-nf", "/nextflow.config", "",
                DescriptorLanguage.NEXTFLOW.getShortName(), "");
        assertNotSame("", workflow.getWorkflowName());

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW, BIOWORKFLOW, null);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER_NEXTFLOW_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);

        // Tests that nf-core nextflow.config files can be parsed
        List<io.dockstore.webservice.core.SourceFile> sourceFileList = fileDAO.findSourceFilesByVersion(refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("nfcore")).findFirst().get().getId());
        assertEquals(4, sourceFileList.size());
        assertTrue(sourceFileList.stream().anyMatch(file -> file.getPath().equals("bin/AMPA-BIGTABLE.pl")) && sourceFileList.stream()
            .anyMatch(file -> file.getPath().equals("bin/multi-AMPA-BIGTABLE.pl")), "files are not what we expected");

        // check that metadata made it through properly
        assertEquals(1, refreshGithub.getAuthors().size());
        assertEquals("test.user@test.com", refreshGithub.getAuthors().get(0).getName());
        assertEquals("Fast automated prediction of protein antimicrobial regions", refreshGithub.getDescription());
    }

    @Test
    void testNextflowYml() {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/ampa-nf", "/nextflow.config", "",
            DescriptorLanguage.NEXTFLOW.getShortName(), "");
        assertNotSame("", workflow.getWorkflowName());

        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TESTING_NEXTFLOW_WORKFLOW, BIOWORKFLOW, null);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(Workflow.DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TESTING_NEXTFLOW_WORKFLOW, BIOWORKFLOW, null);
        Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);

        // check that metadata made it through properly
        Optional<WorkflowVersion> nfcore = refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> workflowVersion.getName().equals("nfcore")).findFirst();
        assertTrue(nfcore.isPresent() &&  "Nextflow !>=23.04.1".equals(nfcore.get().getVersionMetadata().getEngineVersions().get(0)));

        // override
        testingPostgres.runUpdateStatement("update version_metadata set engineversions = 'foobar'");

        // do a github release  and see if the engine version changes
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi openWorkflowApi = new io.dockstore.openapi.client.api.WorkflowsApi(openApiClient);

        handleGitHubRelease(openWorkflowApi, DockstoreTesting.WORKFLOW_NEXTFLOW_DOCKSTORE_YML, "refs/heads/nfcore", USER_1_USERNAME);


        // see what state the metadata is in, before the fix (#5919) this will remain as foobar
        io.dockstore.openapi.client.model.Workflow workflowByPath = openWorkflowApi.getWorkflowByPath(DOCKSTORE_TESTING_NEXTFLOW_WORKFLOW, WorkflowSubClass.BIOWORKFLOW, "versions");
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> opennfcore = workflowByPath.getWorkflowVersions().stream().filter(workflowVersion -> workflowVersion.getName().equals("nfcore"))
            .findFirst();
        assertTrue(opennfcore.isPresent() &&  "Nextflow !>=23.04.1".equals(opennfcore.get().getVersionMetadata().getEngineVersions().get(0)));
    }

    @Test
    void testGitlabNextflowWorkflow() {
        // TODO: need to look into the SlowTest situation but we also need to reactivate the tests against API V4 for 1.5.0
    }

}
