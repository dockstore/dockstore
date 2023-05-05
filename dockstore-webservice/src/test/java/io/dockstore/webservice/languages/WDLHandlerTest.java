package io.dockstore.webservice.languages;

import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.SourceFileHelper;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.WDLHandler.FileInputs;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class WDLHandlerTest {

    public static final String MAIN_WDL = "/GATKSVPipelineClinical.wdl";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @Test
    void getWorkflowContent() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final Version workflow = new WorkflowVersion();

        final String validFilePath = ResourceHelpers.resourceFilePath("valid_description_example.wdl");

        final String goodWdl = FileUtils.readFileToString(new File(validFilePath), StandardCharsets.UTF_8);
        Version version = wdlHandler.parseWorkflowContent(validFilePath, goodWdl, Collections.emptySet(), workflow);
        assertEquals("Mr. Foo", version.getAuthor());
        assertEquals("foo@foo.com", version.getEmail());
        assertEquals("This is a cool workflow trying another line \n## This is a header\n* First Bullet\n* Second bullet", version.getDescription());

        final String invalidFilePath = ResourceHelpers.resourceFilePath("invalid_description_example.wdl");
        final String invalidDescriptionWdl = FileUtils.readFileToString(new File(invalidFilePath), StandardCharsets.UTF_8);
        Version version1 = wdlHandler.parseWorkflowContent(invalidFilePath, invalidDescriptionWdl, Collections.emptySet(), version);
        assertNull(version1.getAuthor());
        assertNull(version1.getEmail());
    }

    @Test
    void getWorkflowContentOfTool() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final Tool tool = new Tool();
        final Tag tag = new Tag();
        final Author author = new Author("Jane Doe");
        author.setEmail("janedoe@example.org");
        tag.setAuthors(Set.of(author));
        // Need to set default version because tool.getAuthors retrieves the default version's authors
        tool.setActualDefaultVersion(tag);
        tool.setDescription("A good description");

        assertEquals(1, tool.getAuthors().size());
        assertTrue(tool.getAuthors().contains(author));
        assertEquals("A good description", tool.getDescription());
        Author actualAuthor = tool.getAuthors().stream().findFirst().get();
        assertEquals("Jane Doe", actualAuthor.getName());
        assertEquals("janedoe@example.org", actualAuthor.getEmail());

        final String invalidFilePath = ResourceHelpers.resourceFilePath("invalid_description_example.wdl");
        final String invalidDescriptionWdl = FileUtils.readFileToString(new File(invalidFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(invalidFilePath, invalidDescriptionWdl, Collections.emptySet(), new WorkflowVersion());

        // Check that parsing an invalid WDL workflow does not corrupt the WDL metadata
        assertEquals(1, tool.getAuthors().size());
        assertTrue(tool.getAuthors().contains(author));
        assertEquals("A good description", tool.getDescription());
        actualAuthor = tool.getAuthors().stream().findFirst().get();
        assertEquals("Jane Doe", actualAuthor.getName());
        assertEquals("janedoe@example.org", actualAuthor.getEmail());
    }

    @Test
    void testRecursiveImports() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        String s = getFileContent("recursive.wdl");
        try {
            wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
            Assertions.fail("Should've detected recursive import");
        } catch (CustomWebApplicationException e) {
            assertEquals(ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT, e.getErrorMessage());
        }

        final File notRecursiveWdl = new File(ResourceHelpers.resourceFilePath("valid_description_example.wdl"));
        s = FileUtils.readFileToString(notRecursiveWdl, StandardCharsets.UTF_8);
        wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
    }


    @Test
    void testRepeatedFilename() throws IOException {
        final String content = getGatkSvMainDescriptorContent();
        final WDLHandler wdlHandler = new WDLHandler();
        Version emptyVersion = new WorkflowVersion();
        final Map<String, SourceFile> map = wdlHandler
                .processImports("whatever", content, emptyVersion, new GatkSvClinicalSourceCodeRepoInterface(), MAIN_WDL);
        // There are 9 Structs.wdl files, in gatk-sv-clinical, but the one in gncv is not imported
        final long structsWdlCount = map.keySet().stream().filter(key -> key.contains("Structs.wdl")).count();
        assertEquals(8, structsWdlCount); // Note: there are 9 Structs.wdl files

        final BioWorkflow entry = new BioWorkflow();
        Version version = wdlHandler.parseWorkflowContent("/GATKSVPipelineClinical.wdl", content, new HashSet<>(map.values()), new WorkflowVersion());
        assertEquals("Christopher Whelan", version.getAuthor());
    }

    @Test
    void testGetToolsForComplexWorkflow() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        Version emptyVersion = new WorkflowVersion();
        final String content = getGatkSvMainDescriptorContent();
        final Map<String, SourceFile> sourceFileMap = wdlHandler
                .processImports("whatever", content, emptyVersion, new GatkSvClinicalSourceCodeRepoInterface(), MAIN_WDL);

        // wdlHandler.getContent ultimately invokes toolDAO.findAllByPath from LanguageHandlerEntry.getURLFromEntry for look
        // up; just have it return null
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        final Optional<String> toolsStr = wdlHandler
                .getContent(MAIN_WDL, content, new HashSet<SourceFile>(sourceFileMap.values()), LanguageHandlerInterface.Type.TOOLS, toolDAO);
        if (toolsStr.isPresent()) {
            final Gson gson = new Gson();
            final Object[] tools = gson.fromJson(toolsStr.get(), Object[].class);
            assertEquals(227, tools.length, "There should be 227 tools");
        } else {
            Assertions.fail("Should be able to get tool json");
        }

    }

    @Test
    void testGetContentWithSyntaxErrors() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final File wdlFile = new File(ResourceHelpers.resourceFilePath("brokenWDL.wdl"));
        final Set<SourceFile> emptySet = Collections.emptySet();

        // wdlHandler.getContent ultimately invokes toolDAO.findAllByPath from LanguageHandlerEntry.getURLFromEntry for look
        // up; just have it return null
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        // run test with a WDL descriptor with syntax errors
        try {
            wdlHandler.getContent("/brokenWDL.wdl", FileUtils.readFileToString(wdlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            Assertions.fail("Expected parsing error");
        } catch (CustomWebApplicationException e) {
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getErrorMessage()).contains(WDLHandler.WDL_PARSE_ERROR);
        }
    }

    private String getGatkSvMainDescriptorContent() throws IOException {
        return getFileContent("gatk-sv-clinical" + MAIN_WDL);
    }

    @Test
    void testFileInputs() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final String md5sumWdl = getFileContent("md5sum.wdl");

        assertEquals(Map.of("ga4ghMd5.inputFile", "File"), wdlHandler.getFileInputs(md5sumWdl, Set.of())
            .get(), "should handle simple file input");

        final String metadataExample2Wdl = getFileContent("metadata_example2.wdl");
        assertEquals(Map.of(
            "metasoft_workflow.allpairs", "Array[File]",
            "metasoft_workflow.signifpairs", "Array[File]"),
            wdlHandler.getFileInputs(metadataExample2Wdl, Set.of())
            .get(), "should handle array inputs");

        assertTrue(wdlHandler.getFileInputs("invalid wdl", Set.of()).isEmpty(),
            "should return Optional.empty for invalid wdl");
    }

    @Test
    void testFileInputValues() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final SourceFile sourceFile = new SourceFile();
        final String testJson = """
            {
              "workflow.file1": "foo.cram",
              "workflow.file2": "goo.cram",
              "workflow.file3": "bar.cram",
              "workflow.files": [
                "file1.cram",
                "file2.cram"
              ]
            }
            """;
        sourceFile.setContent(testJson);
        sourceFile.setAbsolutePath("/test.json");
        final JSONObject jsonObject = SourceFileHelper.testFileAsJsonObject(sourceFile).get();

        assertEquals(0, wdlHandler.fileInputValues(jsonObject, Map.of()).size(),
            "WDL has no file inputs");

        assertEquals(1, wdlHandler.fileInputValues(jsonObject, Map.of("workflow.file1", "File")).size(),
            "Only one parameter defined in WDL");

        // Handles array
        final List<FileInputs> arrayFileInputs =
            wdlHandler.fileInputValues(jsonObject, Map.of("workflow.files", "Array[File]"));
        assertEquals(1, arrayFileInputs.size(), "There should be one file input");
        assertEquals(2, arrayFileInputs.get(0).values().size(),
            "The file input array should have two values");

        assertEquals(0, wdlHandler.fileInputValues(jsonObject, Map.of("workflow.files", "File")).get(0).values().size(),
            "workflow.files is a single file in WDL, an array in test file");

        final List<FileInputs> twoInputs = wdlHandler.fileInputValues(jsonObject,
            Map.of("workflow.files", "Array[File]", "workflow.file3", "File"));
        assertEquals(2, twoInputs.size(), "Handle single file and array");
        // Can't do get(0), order is not deterministic
        final FileInputs arrayInputWithTwoUrls = twoInputs.stream().filter(input -> input.type().equals("Array[File]")).findFirst().get();
        assertEquals(2, arrayInputWithTwoUrls.values().size(), "workflow.files array has 2 possible urls");
        final FileInputs fileInput = twoInputs.stream().filter(input -> input.type().equals("File")).findFirst().get();
        assertEquals(1, fileInput.values().size(), "workfile.file3 has one possible url");
    }

    @Test
    void testFileInputsWithRecursiveImport() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final String fileContent = getFileContent("recursive.wdl");
        final Optional<Map<String, String>> fileInputs = wdlHandler.getFileInputs(fileContent, Set.of());
        assertEquals(Optional.empty(), fileInputs, "Recursive import should not throw");
    }

    private String getFileContent(String path) throws IOException {
        final File wdlFile = new File(ResourceHelpers.resourceFilePath(path));
        return FileUtils.readFileToString(wdlFile, StandardCharsets.UTF_8);
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

        @Override
        public String getReadMeContent(String repositoryId, String branch, String overrideReadMePath) {
            return null;
        }

        @Override
        public String getName() {
            return "gatk";
        }

        @Override
        public void setLicenseInformation(Entry entry, String gitRepository) {

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
        public boolean checkSourceControlTokenValidity() {
            return false;
        }

        @Override
        public Workflow initializeWorkflow(String repositoryId, Workflow workflow) {
            return null;
        }

        @Override
        public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
                Map<String, WorkflowVersion> existingDefaults, Optional<String> versionName, boolean hardRefresh) {
            return null;
        }

        @Override
        public String getDefaultBranch(String repositoryId) {
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


