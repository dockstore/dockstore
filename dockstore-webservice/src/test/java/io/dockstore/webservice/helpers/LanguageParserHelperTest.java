package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.LanguageParsingTest;
import io.dockstore.webservice.core.languageparsing.LanguageParsingRequest;
import io.dockstore.webservice.core.languageparsing.LanguageParsingResponse;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(LanguageParsingTest.NAME)
class LanguageParserHelperTest {

    @Test
    void sendToLambdaSyncTest() throws IOException, InterruptedException {
        LanguageParsingRequest languageParsingRequest = getLanguageParsingRequest();
        LanguageParsingResponse languageParsingResponse = LanguageParserHelper.sendToLambdaSync(languageParsingRequest);
        assertTrue(languageParsingResponse.getVersionTypeValidation().isValid());
        assertTrue(languageParsingResponse.getVersionTypeValidation().isValid());
        assertTrue(languageParsingResponse.getClonedRepositoryAbsolutePath().contains("/tmp"));
        assertFalse(languageParsingResponse.getSecondaryFilePaths().contains("GATKSVPipelineClinical.wdl"), "Main descriptor isn't a secondary file path");
        assertEquals(languageParsingRequest.getBranch(), languageParsingResponse.getLanguageParsingRequest().getBranch());
    }

    /**
     * Tests that an async request can be made without other exceptions thrown. TODO: Somehow test the async response works (lambda hitting
     * web service endpoint)
     */
    @Test
    void sendToLambdaAsyncTest() {
        LanguageParsingRequest languageParsingRequest = getLanguageParsingRequest();
        try {
            LanguageParserHelper.sendToLambdaAsync(languageParsingRequest);
        } catch (Exception e) {
            fail("Should not have any exceptions");
        }
    }

    private LanguageParsingRequest getLanguageParsingRequest() {
        LanguageParsingRequest languageParsingRequest = new LanguageParsingRequest();
        languageParsingRequest.setBranch("dockstore-test");
        languageParsingRequest.setUri("https://github.com/dockstore-testing/gatk-sv-clinical.git");
        languageParsingRequest.setDescriptorRelativePathInGit("GATKSVPipelineClinical.wdl");
        return languageParsingRequest;
    }
}
