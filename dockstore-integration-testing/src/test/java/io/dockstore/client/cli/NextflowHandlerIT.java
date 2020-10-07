package io.dockstore.client.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *  TODO: Use fork with tagged version
 *  TODO: Check paths, something's not right
 */
public class NextflowHandlerIT extends BaseIT{
    protected static SourceCodeRepoInterface sourceCodeRepoInterface;


    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false);
        String githubToken = testingPostgres
                .runSelectStatement("select content from token where username='DockstoreTestUser2' and tokensource='github.com'",
                        String.class);
        sourceCodeRepoInterface = SourceCodeRepoFactory.createGitHubAppRepo(githubToken);
    }

    /**
     * Tests:
     * Nextflow DSL2 with imports that import
     */
    @Test
    public void testProcessImportsRnaseq() {
        final String GITHUB_REPOSITORY = "nextflow-io/rnaseq-nf";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("master");
        workflowVersion.setReference("master");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(GITHUB_REPOSITORY, "nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(GITHUB_REPOSITORY, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "/modules/index.nf", "/modules/multiqc.nf", "/modules/quant.nf", "/modules/fastqc.nf", "/modules/rnaseq.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        Assert.assertEquals( size, stringSourceFileMap.size());
        knownFileNames.forEach(knownFile -> {
            SourceFile sourceFile = stringSourceFileMap.get(knownFile);
            checkSourceFile(sourceFile);
        });

    }

    /**
     * Tests:
     * Nextflow DSL2 with an import statement spans multiple lines
     */
    @Test
    public void testProcessImportsCalliNGS() {
        final String GITHUB_REPOSITORY = "CRG-CNAG/CalliNGS-NF";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("master");
        workflowVersion.setReference("master");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(GITHUB_REPOSITORY, "nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(GITHUB_REPOSITORY, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "bin/gghist.R", "/modules.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        Assert.assertEquals( size, stringSourceFileMap.size());
        knownFileNames.forEach(knownFile -> {
            SourceFile sourceFile = stringSourceFileMap.get(knownFile);
            checkSourceFile(sourceFile);
        });

    }

    /**
     * Tests:
     * Nextflow DSL2 with main descriptor deep inside GitHub repo with import that's up one level that imports something else
     * Import also has the same file name as another file
     */
    @Test
    public void testProcessImportsSamtools() {
        final String GITHUB_REPOSITORY = "nf-core/modules";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("master");
        workflowVersion.setReference("master");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(GITHUB_REPOSITORY, "software/samtools/flagstat/test/nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(GITHUB_REPOSITORY, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/software/samtools/flagstat/test/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "/software/samtools/flagstat/main.nf", "lib/checksum.groovy", "/software/samtools/flagstat/functions.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        Assert.assertEquals( size, stringSourceFileMap.size());
        knownFileNames.forEach(knownFile -> {
            SourceFile sourceFile = stringSourceFileMap.get(knownFile);
            checkSourceFile(sourceFile);
        });

    }

    private void checkSourceFile(SourceFile sourceFile) {
        Assert.assertNotNull(sourceFile.getContent());
        Assert.assertEquals(DescriptorLanguage.FileType.NEXTFLOW, sourceFile.getType());
        Assert.assertTrue(sourceFile.getAbsolutePath().startsWith("/"));
        Assert.assertFalse(sourceFile.getPath().startsWith("/"));
        Assert.assertFalse(sourceFile.getPath().startsWith("./"));
    }

    private void checkAllSourceFiles(Map<String, SourceFile> stringSourceFileMap, int size) {
        Assert.assertEquals(size , stringSourceFileMap.values().stream().map(SourceFile::getPath).distinct().count());
        Assert.assertEquals(size , stringSourceFileMap.values().stream().map(SourceFile::getAbsolutePath).distinct().count());
    }
}
