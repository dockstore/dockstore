package io.dockstore.webservice.languages;

import static org.mockito.Mockito.when;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.language.MinimalLanguageInterface;
import io.dockstore.language.RecommendedLanguageInterface;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

// TODO add coverage for CompleteLanguageInterface
public class LanguagePluginHandlerTest {
    public static final String MAIN_DESCRIPTOR_CWL_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/workflow_docker.cwl";
    public static final String MAIN_DESCRIPTOR_CWL = "/" + MAIN_DESCRIPTOR_CWL_RESOURCE_PATH;
    public static final String SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/complex_computation_docker.cwl";
    public static final String SECONDARY_DESCRIPTOR_CWL = "/" + SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH;
    public static final String PRIMARY_DESCRIPTOR_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/nextflow.config";
    public static final String PRIMARY_DESCRIPTOR = "/" + PRIMARY_DESCRIPTOR_RESOURCE_PATH;
    public static final String DOCKERFILE_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/Dockerfile";
    public static final String DOCKERFILE = "/" + DOCKERFILE_RESOURCE_PATH;
    public static final String TEST_INPUT_FILE_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/workflow.json";
    public static final String TEST_INPUT_FILE = "/" + TEST_INPUT_FILE_RESOURCE_PATH;
    public static final String OTHER_FILE_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/.dockstore.yml";
    public static final String OTHER_FILE = "/" + OTHER_FILE_RESOURCE_PATH;
    public static final String SERVICE_DESCRIPTOR_RESOURCE_PATH = "tools-cwl-workflow-experiments/cwl/service.yml";

    @Test
    public void parseWorkflowContentTest() throws IOException {
        Set<SourceFile> sourceFileSet = new TreeSet<>();

        SourceFile mainDescriptorSourceFile = createSourceFile(MAIN_DESCRIPTOR_CWL, MAIN_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        sourceFileSet.add(mainDescriptorSourceFile);
        SourceFile secondaryDescriptorSourceFile = createSourceFile(SECONDARY_DESCRIPTOR_CWL, SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        sourceFileSet.add(secondaryDescriptorSourceFile);

        LanguagePluginHandler minimalLanguageHandler = new LanguagePluginHandler(TestLanguage.class);
        Version workflowVersion = new WorkflowVersion();
        workflowVersion = minimalLanguageHandler.parseWorkflowContent(SECONDARY_DESCRIPTOR_CWL,
            mainDescriptorSourceFile.getContent(), sourceFileSet, workflowVersion);
        Assert.assertEquals("Shakespeare", workflowVersion.getAuthor());
        Assert.assertEquals("globetheater@bard.com", workflowVersion.getEmail());
    }

    @Test
    public void sourcefilesToIndexedFilesViaValidateWorkflowSetNullTypeTest() throws IOException {
        Set<SourceFile> sourceFileSet = new TreeSet<>();

        SourceFile otherFileSourceFile = createSourceFile(OTHER_FILE, OTHER_FILE_RESOURCE_PATH, FileType.DOCKSTORE_YML);
        sourceFileSet.add(otherFileSourceFile);
        SourceFile primaryFileSourceFile = createSourceFile(PRIMARY_DESCRIPTOR, PRIMARY_DESCRIPTOR_RESOURCE_PATH, FileType.NEXTFLOW_CONFIG);
        sourceFileSet.add(primaryFileSourceFile);
        SourceFile dockerFileSourceFile = createSourceFile(DOCKERFILE, DOCKERFILE_RESOURCE_PATH, FileType.DOCKERFILE);
        sourceFileSet.add(dockerFileSourceFile);
        SourceFile testSourceFile = createSourceFile(TEST_INPUT_FILE, TEST_INPUT_FILE_RESOURCE_PATH, FileType.CWL_TEST_JSON);
        sourceFileSet.add(testSourceFile);
        SourceFile mainDescriptorSourceFile = createSourceFile(MAIN_DESCRIPTOR_CWL, MAIN_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        sourceFileSet.add(mainDescriptorSourceFile);

        SourceFile secondaryDescriptorSourceFile = createSourceFile(SECONDARY_DESCRIPTOR_CWL, SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        // setting the file type to null should cause an exception
        // in the LanguagePluginHandler sourcefilesToIndexedFiles method
        secondaryDescriptorSourceFile.setType(null);
        sourceFileSet.add(secondaryDescriptorSourceFile);

        LanguagePluginHandler minimalLanguageHandler = new LanguagePluginHandler(TestLanguage.class);

        try {
            minimalLanguageHandler.validateWorkflowSet(sourceFileSet, SECONDARY_DESCRIPTOR_CWL);
            Assert.fail("Expected method error");
        } catch (CustomWebApplicationException e) {
            // ValidateWorkflowSet can intercept the original exception and
            // return HttpStatus.SC_UNPROCESSABLE_ENTITY
            Assert.assertTrue("Did not get correct status code",
                e.getResponse().getStatus() == HttpStatus.SC_UNPROCESSABLE_ENTITY
                    || e.getResponse().getStatus() == HttpStatus.SC_METHOD_FAILURE);
        }

        secondaryDescriptorSourceFile.setType(FileType.DOCKSTORE_CWL);
        sourceFileSet.clear();

        sourceFileSet.add(otherFileSourceFile);
        sourceFileSet.add(primaryFileSourceFile);
        sourceFileSet.add(mainDescriptorSourceFile);
        sourceFileSet.add(secondaryDescriptorSourceFile);
        sourceFileSet.add(testSourceFile);
        sourceFileSet.add(dockerFileSourceFile);
        VersionTypeValidation versionTypeValidation = minimalLanguageHandler.validateWorkflowSet(sourceFileSet, MAIN_DESCRIPTOR_CWL);
        Assert.assertTrue(versionTypeValidation.isValid());

        sourceFileSet.clear();
        // Missing main descriptor should cause an invalid version validation
        sourceFileSet.add(secondaryDescriptorSourceFile);
        versionTypeValidation = minimalLanguageHandler.validateWorkflowSet(sourceFileSet, MAIN_DESCRIPTOR_CWL);
        Assert.assertTrue(!versionTypeValidation.isValid()
            && versionTypeValidation.getMessage().containsValue("Primary descriptor file not found."));

    }


    // TODO add coverage for CompleteLanguageInterface
    @Test
    public void getContentTest() throws IOException {
        Set<SourceFile> secondarySourceFiles = new TreeSet<>();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        SourceFile mainDescriptorSourceFile = createSourceFile(MAIN_DESCRIPTOR_CWL, MAIN_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        SourceFile secondaryDescriptorSourceFile = createSourceFile(SECONDARY_DESCRIPTOR_CWL, SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        secondarySourceFiles.add(secondaryDescriptorSourceFile);

        LanguagePluginHandler minimalLanguageHandler = new LanguagePluginHandler(TestLanguage.class);
        Optional<String> content = Optional.empty();
        try {
            content = minimalLanguageHandler.getContent(MAIN_DESCRIPTOR_CWL,
                mainDescriptorSourceFile.getContent(), secondarySourceFiles,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
        } catch (CustomWebApplicationException e) {
            Assert.fail("getContentTest should have passed");
        }
        Assert.assertTrue(content.isEmpty());
    }

    @Test
    public void processImportsTest() throws IOException {
        SourceFile mainDescriptorSourceFile = createSourceFile(MAIN_DESCRIPTOR_CWL, MAIN_DESCRIPTOR_CWL_RESOURCE_PATH, FileType.DOCKSTORE_CWL);
        LanguagePluginHandler minimalLanguageHandler = new LanguagePluginHandler(TestLanguage.class);
        Version emptyVersion = new WorkflowVersion();
        Map<String, SourceFile> importedFilesMap = minimalLanguageHandler.processImports("whatever", mainDescriptorSourceFile.getContent(),
                emptyVersion, new ToolsCWLWorkflowExpSourceCodeRepoInterface(), MAIN_DESCRIPTOR_CWL);
        SourceFile secondarySourceFile = importedFilesMap.get(SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH);
        Assert.assertSame(FileType.DOCKSTORE_CWL, secondarySourceFile.getType());
        SourceFile testInputFile = importedFilesMap.get(TEST_INPUT_FILE_RESOURCE_PATH);
        Assert.assertSame(FileType.CWL_TEST_JSON, testInputFile.getType());
        SourceFile dockerFile = importedFilesMap.get(DOCKERFILE_RESOURCE_PATH);
        Assert.assertSame(FileType.DOCKERFILE, dockerFile.getType());
        SourceFile serviceFile = importedFilesMap.get(SERVICE_DESCRIPTOR_RESOURCE_PATH);
        // eventually will be DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML
        // when services are enabled?
        Assert.assertSame(FileType.DOCKSTORE_CWL, serviceFile.getType());

    }

    public SourceFile createSourceFile(String filePath, String fileResourcePath, FileType fileType) throws IOException {
        File resourceFile = new File(ResourceHelpers.resourceFilePath(fileResourcePath));
        String sourceFileContents = Files.asCharSource(resourceFile, Charsets.UTF_8).read();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(sourceFileContents);
        sourceFile.setAbsolutePath(filePath);
        sourceFile.setPath(filePath);
        return sourceFile;
    }

    public static class TestLanguage implements RecommendedLanguageInterface {

        // MinimalLanguageInterface
        @Override
        public DescriptorLanguage getDescriptorLanguage() {
            return DescriptorLanguage.CWL;
        }

        @Override
        public Pattern initialPathPattern() {
            return null;
        }

        @Override
        public Map<String, Pair<String, GenericFileType>> indexWorkflowFiles(String initialPath,
            String contents, FileReader reader) {
            Map<String, Pair<String, GenericFileType>> results = new HashMap<>();

            // fake getting the imported descriptor contents from the main descriptor
            String importedContents = reader.readFile(SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH);
            results.put(SECONDARY_DESCRIPTOR_CWL_RESOURCE_PATH, new ImmutablePair<>(importedContents, GenericFileType.IMPORTED_DESCRIPTOR));
            String testFileContents = reader.readFile(TEST_INPUT_FILE_RESOURCE_PATH);
            results.put(TEST_INPUT_FILE_RESOURCE_PATH, new ImmutablePair<>(testFileContents, GenericFileType.TEST_PARAMETER_FILE));
            String dockerfileContents = reader.readFile(DOCKERFILE_RESOURCE_PATH);
            results.put(DOCKERFILE_RESOURCE_PATH, new ImmutablePair<>(dockerfileContents, GenericFileType.CONTAINERFILE));
            String serviceContents = reader.readFile(SERVICE_DESCRIPTOR_RESOURCE_PATH);
            results.put(SERVICE_DESCRIPTOR_RESOURCE_PATH, new ImmutablePair<>(serviceContents, GenericFileType.IMPORTED_DESCRIPTOR));
            return results;
        }

        @Override
        public WorkflowMetadata parseWorkflowForMetadata(String initialPath, String contents,
            Map<String, Pair<String, GenericFileType>> indexedFiles) {
            WorkflowMetadata workflowMetadata = new WorkflowMetadata();
            workflowMetadata.setAuthor("Shakespeare");
            workflowMetadata.setEmail("globetheater@bard.com");
            return workflowMetadata;
        }


        // RecommendedLanguageInterface
        @Override
        public String launchInstructions(String trsID) {
            return null;
        }

        @Override
        public VersionTypeValidation validateWorkflowSet(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles) {
            // This will be executed via the sourcefilesToIndexedFilesViaValidateWorkflowSetNullTypeTest test code
            // and some files may not be present depending on the inputs to the test
            if (indexedFiles.containsKey(MAIN_DESCRIPTOR_CWL)) {
                Assert.assertSame(MinimalLanguageInterface.GenericFileType.IMPORTED_DESCRIPTOR, indexedFiles.get(MAIN_DESCRIPTOR_CWL).getRight());
            }
            if (indexedFiles.containsKey(SECONDARY_DESCRIPTOR_CWL)) {
                Assert.assertSame(MinimalLanguageInterface.GenericFileType.IMPORTED_DESCRIPTOR, indexedFiles.get(SECONDARY_DESCRIPTOR_CWL).getRight());
            }
            if (indexedFiles.containsKey(TEST_INPUT_FILE)) {
                Assert.assertSame(GenericFileType.TEST_PARAMETER_FILE, indexedFiles.get(TEST_INPUT_FILE).getRight());
            }
            if (indexedFiles.containsKey(DOCKERFILE)) {
                Assert.assertSame(GenericFileType.CONTAINERFILE, indexedFiles.get(DOCKERFILE).getRight());
            }
            if (indexedFiles.containsKey(PRIMARY_DESCRIPTOR)) {
                Assert.assertSame(GenericFileType.IMPORTED_DESCRIPTOR, indexedFiles.get(PRIMARY_DESCRIPTOR).getRight());
            }
            if (indexedFiles.containsKey(OTHER_FILE)) {
                Assert.assertSame(GenericFileType.IMPORTED_DESCRIPTOR, indexedFiles.get(OTHER_FILE).getRight());
            }

            return new VersionTypeValidation(true, Collections.emptyMap());
        }

        @Override
        public VersionTypeValidation validateTestParameterSet(Map<String, Pair<String, GenericFileType>> indexedFiles) {
            return null;
        }
    }

    /**
     * Reads files from /tools-cwl-workflow-experiments/cwl directory in resources. Only need to truly implement two methods; the rest are
     * never called from parseWorkflowContent
     */
    private static class ToolsCWLWorkflowExpSourceCodeRepoInterface extends SourceCodeRepoInterface {
        @Override
        public String readGitRepositoryFile(String repositoryId, DescriptorLanguage.FileType fileType, Version version,
            String specificPath) {
            return this.readFile(repositoryId, specificPath, null);
        }

        @Override
        public String readFile(String repositoryId, String fileName, String reference) {
            try {
                final File file = new File(ResourceHelpers.resourceFilePath(fileName));
                return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String getREADMEContent(String repositoryId, String branch) {
            return null;
        }

        @Override
        public String getName() {
            return "toolscwlworkflowexperiments";
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
        public boolean checkSourceCodeValidity() {
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
