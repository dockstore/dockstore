package io.dockstore.webservice.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DockerImageReference;
import io.dockstore.common.DockerParameter;
import io.dropwizard.testing.FixtureHelpers;
import java.util.Map;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class NextflowHandlerTest {

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
}
