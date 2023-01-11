package io.dockstore.webservice.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DockerImageReference;
import io.dockstore.common.DockerParameter;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dropwizard.testing.FixtureHelpers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class NextflowHandlerTest {

    private static final String DSL_V1 = "1";
    private static final String DSL_V2 = "2";

    @Test
    void testGetRelativeImportPathFromLine() {
        assertEquals("modules/rnaseq.nf", NextflowHandler.getRelativeImportPathFromLine("include { RNASEQ } from './modules/rnaseq'", "/main.nf"));
        assertEquals("modules/rnaseq.nf", NextflowHandler.getRelativeImportPathFromLine("include { RNASEQ } from './modules/rnaseq.nf'", "/main.nf"));
        // TODO: Replace with Java 12's http://openjdk.java.net/jeps/326
        assertEquals("modules.nf", NextflowHandler.getRelativeImportPathFromLine("include { \n"
                + "  PREPARE_GENOME_SAMTOOLS;\n" + "  PREPARE_GENOME_PICARD; \n" + "  PREPARE_STAR_GENOME_INDEX;\n"
                + "  PREPARE_VCF_FILE;\n" + "  RNASEQ_MAPPING_STAR;\n" + "  RNASEQ_GATK_SPLITNCIGAR; \n" + "  RNASEQ_GATK_RECALIBRATE;\n"
                + "  RNASEQ_CALL_VARIANTS;\n" + "  POST_PROCESS_VCF;\n" + "  PREPARE_VCF_FOR_ASE;\n" + "  ASE_KNOWNSNPS;\n"
                + "  group_per_sample } from './modules.nf'", "/main.nf"));
    }

    @Test
    void testImportPattern() {
        String content = "#include { RNASEQ } from './modules/rnaseq'\", \"/main.nf";
        Matcher m = NextflowHandler.IMPORT_PATTERN.matcher(content);
        boolean matches = m.matches();
        assertFalse(matches);
        content = "     include { RNASEQ } from './modules/rnaseq'";
        m = NextflowHandler.IMPORT_PATTERN.matcher(content);
        matches = m.matches();
        assertTrue(matches);
    }

    @Test
    void testDockerImageReference() {
        NextflowHandler nextflowHandler = new NextflowHandler();
        final String dockerImagesNextflow = FixtureHelpers.fixture("fixtures/dockerImages.nf");
        final Map<String, DockerParameter> callsToDockerMap = nextflowHandler.getCallsToDockerMap(dockerImagesNextflow, "");

        callsToDockerMap.entrySet().stream().forEach(entry -> {
            if ("parameterizedDocker".equals(entry.getKey())) {
                assertEquals(DockerImageReference.DYNAMIC, entry.getValue().imageReference());
            } else {
                assertEquals(DockerImageReference.LITERAL, entry.getValue().imageReference());
            }
        });
    }

    @Test
    void testDslVersion() {
        final NextflowHandler nextflowHandler = new NextflowHandler();
        final String dsl1 = """
            #!/usr/bin/env nextflow
            nextflow.enable.dsl=1
            exit 0
            """;
        assertEquals(DSL_V1, nextflowHandler.getDslVersion(dsl1).get());

        final String dsl2 = dsl1.replace(DSL_V1, DSL_V2);
        assertEquals(DSL_V2, nextflowHandler.getDslVersion(dsl2).get());

        final String implicitDsl = """
            #!/usr/bin/env nextflow
            exit 0
            """;
        assertEquals(Optional.empty(), nextflowHandler.getDslVersion(implicitDsl));

        final String dsl1And2 = """
            #!/usr/bin/env nextflow
            nextflow.enable.dsl=1
            nextflow.enable.dsl=2
            exit 0
            """;
        assertEquals(DSL_V1, nextflowHandler.getDslVersion(dsl1And2).get(), "First dsl declaration should win");

    }

    @Test
    void testParseWorkflowContent() {
        final NextflowHandler nextflowHandler = new NextflowHandler();
        final String config = FixtureHelpers.fixture("fixtures/nextflow.config");
        final WorkflowVersion version = new WorkflowVersion();
        final String mainContent = FixtureHelpers.fixture("fixtures/main.nf");
        final SourceFile mainSourceFile = new SourceFile();
        mainSourceFile.setContent(mainContent);
        mainSourceFile.setPath("main.nf");
        final SourceFile secondarySourceFile = new SourceFile();
        final Set<SourceFile> sourceFiles = Set.of(mainSourceFile, secondarySourceFile);

        nextflowHandler.parseWorkflowContent("/main.nf", config, sourceFiles, version);
        assertEquals(List.of(DSL_V2), version.getDescriptorTypeVersions());
        sourceFiles.forEach(sourceFile -> assertEquals(DSL_V2, sourceFile.getTypeVersion()));

        mainSourceFile.setContent(mainContent.replace("nextflow.enable.dsl = 2", "nextflow.enable.dsl = 1"));
        nextflowHandler.parseWorkflowContent("/main.nf", config, sourceFiles, version);
        assertEquals(List.of(DSL_V1), version.getDescriptorTypeVersions());
        sourceFiles.forEach(sourceFile -> assertEquals(DSL_V1, sourceFile.getTypeVersion()));

        // No DSL specified
        mainSourceFile.setContent(mainContent.replace("nextflow.enable.dsl = 2", ""));
        nextflowHandler.parseWorkflowContent("/main.nf", config, sourceFiles, version);
        assertEquals(List.of(), version.getDescriptorTypeVersions());
        sourceFiles.forEach(sourceFile -> assertNull(sourceFile.getTypeVersion()));

        // Descriptor referenced by config does not exist
        mainSourceFile.setPath("/notthemain.nf");
        nextflowHandler.parseWorkflowContent("/main.nf", config, sourceFiles, version);
        assertEquals(List.of(), version.getDescriptorTypeVersions());
        sourceFiles.forEach(sourceFile -> assertNull(sourceFile.getTypeVersion()));
    }
}
