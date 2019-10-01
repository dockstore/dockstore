package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT;

public class WDLHandlerTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    public void getWorkflowContent() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final Workflow workflow = new BioWorkflow();
        workflow.setAuthor("Jane Doe");
        workflow.setDescription("A good description");
        workflow.setEmail("janedoe@example.org");

        final String validFilePath = ResourceHelpers.resourceFilePath("valid_description_example.wdl");

        final String goodWdl = FileUtils.readFileToString(new File(validFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(workflow, validFilePath, goodWdl, Collections.emptySet(), new WorkflowVersion());
        Assert.assertEquals(workflow.getAuthor(), "Mr. Foo");
        Assert.assertEquals(workflow.getEmail(), "foo@foo.com");
        Assert.assertEquals(workflow.getDescription(),
                "This is a cool workflow trying another line \n## This is a header\n* First Bullet\n* Second bullet");


        final String invalidFilePath = ResourceHelpers.resourceFilePath("invalid_description_example.wdl");
        final String invalidDescriptionWdl = FileUtils.readFileToString(new File(invalidFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(workflow, invalidFilePath, invalidDescriptionWdl, Collections.emptySet(), new WorkflowVersion());
        Assert.assertNull(workflow.getAuthor());
        Assert.assertNull(workflow.getEmail());
    }

    @Test
    public void getWorkflowContentOfTool() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final Tool tool = new Tool();
        tool.setAuthor("Jane Doe");
        tool.setDescription("A good description");
        tool.setEmail("janedoe@example.org");

        Assert.assertEquals("Jane Doe", tool.getAuthor());
        Assert.assertEquals("A good description", tool.getDescription());
        Assert.assertEquals("janedoe@example.org", tool.getEmail());

        final String invalidFilePath = ResourceHelpers.resourceFilePath("invalid_description_example.wdl");
        final String invalidDescriptionWdl = FileUtils.readFileToString(new File(invalidFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(tool, invalidFilePath, invalidDescriptionWdl, Collections.emptySet(), new WorkflowVersion());

        // Check that parsing an invalid WDL workflow does not corrupt the CWL metadata
        Assert.assertEquals("Jane Doe", tool.getAuthor());
        Assert.assertEquals("A good description", tool.getDescription());
        Assert.assertEquals("janedoe@example.org", tool.getEmail());
    }

    @Test
    public void testRecursiveImports() throws IOException {
        final File recursiveWdl = new File(ResourceHelpers.resourceFilePath("recursive.wdl"));

        final WDLHandler wdlHandler = new WDLHandler();
        String s = FileUtils.readFileToString(recursiveWdl, StandardCharsets.UTF_8);
        try {
            wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
            Assert.fail("Should've detected recursive import");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT, e.getErrorMessage());
        }

        final File notRecursiveWdl = new File(ResourceHelpers.resourceFilePath("valid_description_example.wdl"));
        s = FileUtils.readFileToString(notRecursiveWdl, StandardCharsets.UTF_8);
        wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
    }


    @Test
    public void testRepeatedFilename() throws IOException {
        final String mainWdl = "/GATKSVPipelineClinical.wdl";
        final File wdlFile = new File(ResourceHelpers.resourceFilePath("gatk-sv-clinical" + mainWdl));
        final WDLHandler wdlHandler = new WDLHandler();
        final String content = FileUtils.readFileToString(wdlFile, StandardCharsets.UTF_8);
        final Map<String, SourceFile> map = wdlHandler
                .processImports("whatever", content, null, new GatkSvClinicalSourceCodeRepoInterface(), mainWdl);
        // There are 9 Structs.wdl files, in gatk-sv-clinical, but the one in gncv is not imported
        final long structsWdlCount = map.keySet().stream().filter(key -> key.contains("Structs.wdl")).count();
        Assert.assertEquals(8, structsWdlCount); // Note: there are 9 Structs.wdl files

        final BioWorkflow entry = new BioWorkflow();
        wdlHandler.parseWorkflowContent(entry, "/", content, new HashSet<>(map.values()), null);
        Assert.assertEquals("Christopher Whelan", entry.getAuthor());
    }

    /**
     * Reads files from gatk-sv-clinical directory in resources. Only need to truly implement two methods; the rest are
     * never called from WDLHandler.parseWorkflowContent
     */
    private static class GatkSvClinicalSourceCodeRepoInterface extends SourceCodeRepoInterface {
        @Override
        public String readGitRepositoryFile(String repositoryId, DescriptorLanguage.FileType fileType, Version version,
                String specificPath) {
            return this.readFile(repositoryId, specificPath, null);
        }

        @Override
        public String readFile(String repositoryId, String fileName, String reference) {
            try {
                final File file = new File(ResourceHelpers.resourceFilePath("gatk-sv-clinical" + fileName));
                return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }

        // From here on down these methods are not invoked in our tests
        @Override
        public List<String> listFiles(String repositoryId, String pathToDirectory, String reference) {
            return null;
        }

        @Override
        public Map<String, String> getWorkflowGitUrl2RepositoryId() {
            return null;
        }

        @Override
        public boolean checkSourceCodeValidity() {
            return false;
        }

        @Override
        public Workflow initializeWorkflow(String repositoryId, Workflow workflow) {
            return null;
        }

        @Override
        public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
                Map<String, WorkflowVersion> existingDefaults) {
            return null;
        }

        @Override
        public String getRepositoryId(Entry entry) {
            return null;
        }

        @Override
        public String getMainBranch(Entry entry, String repositoryId) {
            return null;
        }

        @Override
        public SourceFile getSourceFile(String path, String id, String branch, DescriptorLanguage.FileType type) {
            return null;
        }

        @Override
        public void updateReferenceType(String repositoryId, Version version) {

        }
        @Override
        public String getCommitID(String repositoryId, Version version) {
            return null;
        }
    }


}


