/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.languages.NextflowHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *  TODO: Check paths, it looks like it's relative to the file that imported it but this means it won't be unique
 */
class NextflowHandlerIT extends BaseIT {
    protected static SourceCodeRepoInterface sourceCodeRepoInterface;


    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false, testingPostgres);
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
    void testProcessImportsRnaseq() {
        final String githubRepository = "dockstore-testing/rnaseq-nf";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("2.0.1");
        workflowVersion.setReference("2.0.1");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(githubRepository, "nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(githubRepository, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "/modules/index.nf", "/modules/multiqc.nf", "/modules/quant.nf", "/modules/fastqc.nf", "/modules/rnaseq.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        assertEquals(size, stringSourceFileMap.size());
        knownFileNames.forEach(knownFile -> {
            SourceFile sourceFile = stringSourceFileMap.get(knownFile);
            checkSourceFile(sourceFile);
        });

        NextflowHandler nextflowHandler = new NextflowHandler();
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        Session session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
        ToolDAO toolDAO = new ToolDAO(sessionFactory);
        Optional<String> content = nextflowHandler
                .getContent("main.nf", mainDescriptorContents, new HashSet<>(stringSourceFileMap.values()), LanguageHandlerInterface.Type.TOOLS, toolDAO);
        assertEquals(
            "[{\"id\":\"FASTQC\",\"file\":\"main.nf\",\"docker\":\"nextflow/rnaseq-nf:latest\",\"link\":\"https://hub.docker.com/r/nextflow/rnaseq-nf\",\"specifier\":\"LATEST\"},{\"id\":\"MULTIQC\",\"file\":\"main.nf\",\"docker\":\"nextflow/rnaseq-nf:latest\",\"link\":\"https://hub.docker.com/r/nextflow/rnaseq-nf\",\"specifier\":\"LATEST\"},{\"id\":\"INDEX\",\"file\":\"main.nf\",\"docker\":\"nextflow/rnaseq-nf:latest\",\"link\":\"https://hub.docker.com/r/nextflow/rnaseq-nf\",\"specifier\":\"LATEST\"},{\"id\":\"QUANT\",\"file\":\"main.nf\",\"docker\":\"nextflow/rnaseq-nf:latest\",\"link\":\"https://hub.docker.com/r/nextflow/rnaseq-nf\",\"specifier\":\"LATEST\"}]",
            content.get());


    }

    /**
     * Tests:
     * Nextflow DSL2 with an import statement spans multiple lines
     */
    @Test
    void testProcessImportsCalliNGS() {
        final String githubRepository = "dockstore-testing/CalliNGS-NF";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("1.0.1");
        workflowVersion.setReference("1.0.1");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(githubRepository, "nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(githubRepository, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "bin/gghist.R", "/modules.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        assertEquals(size, stringSourceFileMap.size());
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
    void testProcessImportsSamtools() {
        final String githubRepository = "dockstore-testing/modules";
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("0.0.1");
        workflowVersion.setReference("0.0.1");
        String mainDescriptorContents = sourceCodeRepoInterface.readFile(githubRepository, "software/samtools/flagstat/test/nextflow.config", "master");
        Map<String, SourceFile> stringSourceFileMap = sourceCodeRepoInterface
                .resolveImports(githubRepository, mainDescriptorContents, DescriptorLanguage.FileType.NEXTFLOW_CONFIG, workflowVersion, "/software/samtools/flagstat/test/nextflow.config");
        List<String> knownFileNames = Arrays.asList("main.nf", "/software/samtools/flagstat/main.nf", "lib/checksum.groovy", "/software/samtools/flagstat/functions.nf");
        int size = knownFileNames.size();
        checkAllSourceFiles(stringSourceFileMap, size);
        assertEquals(size, stringSourceFileMap.size());
        knownFileNames.forEach(knownFile -> {
            SourceFile sourceFile = stringSourceFileMap.get(knownFile);
            checkSourceFile(sourceFile);
        });

    }

    private void checkSourceFile(SourceFile sourceFile) {
        assertNotNull(sourceFile.getContent());
        assertEquals(FileType.NEXTFLOW, sourceFile.getType());
        assertTrue(sourceFile.getAbsolutePath().startsWith("/"));
        assertFalse(sourceFile.getPath().startsWith("/"));
        assertFalse(sourceFile.getPath().startsWith("./"));
    }

    private void checkAllSourceFiles(Map<String, SourceFile> stringSourceFileMap, int size) {
        assertEquals(size, stringSourceFileMap.values().stream().map(SourceFile::getPath).distinct().count());
        assertEquals(size, stringSourceFileMap.values().stream().map(SourceFile::getAbsolutePath).distinct().count());
    }
}
