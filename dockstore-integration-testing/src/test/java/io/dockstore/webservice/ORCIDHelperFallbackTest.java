package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.ORCID_USER_3;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.webservice.core.OrcidAuthorInformation;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * This tests using the public ORCID api when the members API is unavailable.
 */
@Tag(NonConfidentialTest.NAME)
class ORCIDHelperFallbackTest {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    void testOrcidAuthorNoCreds() {
        Optional<OrcidAuthorInformation> orcidAuthorInformation = ORCIDHelper.getOrcidAuthorInformation(ORCID_USER_3, null);
        assertTrue(orcidAuthorInformation.isPresent(), "Should be able to get Orcid Author information");
        assertNotNull(orcidAuthorInformation.get().getOrcid());
        assertNotNull(orcidAuthorInformation.get().getName());
        assertNotNull(orcidAuthorInformation.get().getAffiliation());
        assertNotNull(orcidAuthorInformation.get().getRole());
    }
}
