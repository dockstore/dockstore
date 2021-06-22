package io.dockstore.webservice.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.dockstore.common.LanguageParsingTest;
import io.dockstore.webservice.core.languageParsing.LanguageParsingRequest;
import io.dockstore.webservice.core.languageParsing.LanguageParsingResponse;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(LanguageParsingTest.class)
public class LanguageParserHelperTest {

    @Test
    public void sendToLambdaSyncTest() throws IOException, InterruptedException {
        LanguageParsingRequest languageParsingRequest = getLanguageParsingRequest();
        LanguageParsingResponse languageParsingResponse = LanguageParserHelper.sendToLambdaSync(languageParsingRequest);
        Assert.assertTrue(languageParsingResponse.getVersionTypeValidation().isValid());
        assertTrue(languageParsingResponse.getVersionTypeValidation().isValid());
        assertTrue(languageParsingResponse.getClonedRepositoryAbsolutePath().contains("/tmp"));
        assertFalse(
            "Main descriptor isn't a secondary file path",
            languageParsingResponse.getSecondaryFilePaths().contains("GATKSVPipelineClinical.wdl"));
        Assert.assertEquals(languageParsingRequest.getBranch(), languageParsingResponse.getLanguageParsingRequest().getBranch());
    }

    /**
     * Tests that an async request can be made without other exceptions thrown. TODO: Somehow test the async response works (lambda hitting
     * web service endpoint)
     */
    @Test
    public void sendToLambdaAsyncTest() {
        LanguageParsingRequest languageParsingRequest = getLanguageParsingRequest();
        try {
            LanguageParserHelper.sendToLambdaAsync(languageParsingRequest);
        } catch (Exception e) {
            Assert.fail("Should not have any exceptions");
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
