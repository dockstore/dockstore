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

package core;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

/**
 * @author dyuen
 */
@Ignore("Has not been working since at least 1.3.0")
public class CRUDTest {

    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstore.yml");
    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, CONFIG_PATH);
    private static final String DOCKERFILE_CONTENT = "This is the content of the Dockerfile";
    private static final String CWL_CONTENT = "This is the content of the CWL file";
    private static final String CWL_TEST_CONTENT = "This is the content of the CWL test file";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    private DockstoreWebserviceApplication application;
    private Session session;

    @Before
    public void clearDB() throws Exception {
        clearState();
        application = RULE.getApplication();

        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    private void flushSession() {
        session.flush();
        session.close();
        session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    /**
     * Clears database state and known queues for testing.
     **/
    private static void clearState() throws Exception {
        RULE.getApplication().run("db", "drop-all", "--confirm-delete-everything", CONFIG_PATH);
    }

    @Test
    public void testWorkflowCreateAndPersist() {
        final WorkflowDAO workflowDAO = new WorkflowDAO(application.getHibernate().getSessionFactory());
        final Workflow workflow = new Workflow();
        workflow.setWorkflowName("foobar");
        final WorkflowVersionDAO workflowVersionDAO = new WorkflowVersionDAO(application.getHibernate().getSessionFactory());
        final WorkflowVersion workflowVersion = new WorkflowVersion();

        final long l1 = workflowVersionDAO.create(workflowVersion);

        flushSession();

        final WorkflowVersion version = workflowVersionDAO.findById(l1);
        workflow.getWorkflowVersions().add(version);
        workflowDAO.create(workflow);

        flushSession();

        final List<Workflow> all = workflowDAO.findAll();
        Assert.assertTrue("should find one workflow, found " + all.size(), all.size() == 1);
        final int versionsSize = all.get(0).getWorkflowVersions().size();
        Assert.assertTrue("should find one workflow version, found " + versionsSize, versionsSize == 1);
    }

    @Test
    public void testGA4GHDockerfileFormats() throws IOException {
        createTestDataStructure();
        String targetURL = "/tools/quay.io%2Fnamespace%2Fname%2Ftoolname/versions/versionName/dockerfile";
        checkEndpointForContentType(targetURL, DOCKERFILE_CONTENT);
    }

    @Test
    public void testGA4GHDescriptorFormats() throws IOException {
        createTestDataStructure();
        String targetURL = "/tools/quay.io%2Fnamespace%2Fname%2Ftoolname/versions/versionName/CWL/descriptor";
        checkEndpointForContentType(targetURL, CWL_CONTENT);
    }

    @Test
    public void testGA4GHTestFormats() throws IOException {
        createTestDataStructure();
        String targetURL = "/tools/quay.io%2Fnamespace%2Fname%2Ftoolname/versions/versionName/CWL/tests";
        checkEndpointForContentType(targetURL, CWL_TEST_CONTENT);
    }

    private void checkEndpointForContentType(String targetURL, String contentCheck) throws IOException {
        URL url = new URL("http://localhost:" + RULE.getLocalPort() + DockstoreWebserviceApplication.GA4GH_API_PATH + targetURL);
        String collect = Resources.readLines(url, Charset.forName("UTF-8")).stream().collect(Collectors.joining());
        // json version is very verbose
        Assert.assertTrue((collect.startsWith("{") || collect.startsWith("[")) && collect.length() > contentCheck.length());
        // plain versions
        // 1) plain version via the Accept
        HttpGet get = new HttpGet("http://localhost:" + RULE.getLocalPort() + DockstoreWebserviceApplication.GA4GH_API_PATH + targetURL);
        get.setHeader("Accept", "text/plain");
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpResponse execute = httpClient.execute(get);
        String result = IOUtils.toString(execute.getEntity().getContent(), StandardCharsets.UTF_8);
        Assert.assertTrue(Objects.equals(result, contentCheck));
        // 2) plain version via parameter
        if (targetURL.contains("CWL")) {
            get = new HttpGet("http://localhost:" + RULE.getLocalPort() + DockstoreWebserviceApplication.GA4GH_API_PATH + targetURL
                    .replace("CWL", "plain-CWL"));
            execute = httpClient.execute(get);
            result = IOUtils.toString(execute.getEntity().getContent(), StandardCharsets.UTF_8);
            Assert.assertTrue(Objects.equals(result, contentCheck));
        }
    }

    private void createTestDataStructure() {
        final ToolDAO toolDAO = new ToolDAO(application.getHibernate().getSessionFactory());
        Tool tool = new Tool();
        tool.setRegistry(Registry.QUAY_IO);
        tool.setNamespace("namespace");
        tool.setName("name");
        tool.setToolname("toolname");
        tool.setIsPublished(true);

        final VersionDAO<Tag> toolVersionDAO = new VersionDAO<>(application.getHibernate().getSessionFactory());
        Tag toolVersion = new Tag();
        toolVersion.setName("versionName");
        toolVersion.setReference("versionName");
        toolVersion.setValid(true);
        toolVersion.setVerified(true);
        toolVersion.setImageId("imageid");
        toolVersion.setHidden(false);

        final long toolVersionId = toolVersionDAO.create(toolVersion);
        toolVersion = toolVersionDAO.findById(toolVersionId);

        flushSession();

        FileDAO fileDAO = new FileDAO(application.getHibernate().getSessionFactory());
        SourceFile file = new SourceFile();
        file.setType(SourceFile.FileType.DOCKERFILE);
        file.setContent(DOCKERFILE_CONTENT);
        file.setPath("/Dockerfile");
        long dockerFileId = fileDAO.create(file);

        SourceFile cwlFile = new SourceFile();
        cwlFile.setType(SourceFile.FileType.DOCKSTORE_CWL);
        cwlFile.setContent(CWL_CONTENT);
        cwlFile.setPath("/Dockstore.cwl");
        long cwlFileId = fileDAO.create(cwlFile);

        SourceFile cwlTestFile = new SourceFile();
        cwlTestFile.setType(SourceFile.FileType.CWL_TEST_JSON);
        cwlTestFile.setContent(CWL_TEST_CONTENT);
        cwlTestFile.setPath("/test.json");
        long cwlTestFileId = fileDAO.create(cwlTestFile);

        flushSession();

        long toolId = toolDAO.create(tool);
        tool = toolDAO.findById(toolId);
        tool.getTags().add(toolVersion);

        flushSession();

        toolVersion = toolVersionDAO.findById(toolVersionId);
        file = fileDAO.findById(dockerFileId);
        cwlFile = fileDAO.findById(cwlFileId);
        cwlTestFile = fileDAO.findById(cwlTestFileId);
        toolVersion.getSourceFiles().add(file);
        toolVersion.getSourceFiles().add(cwlFile);
        toolVersion.getSourceFiles().add(cwlTestFile);

        flushSession();
    }
}
