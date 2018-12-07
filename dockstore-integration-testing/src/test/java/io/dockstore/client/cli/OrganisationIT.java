package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.OrganisationsApi;
import io.swagger.client.model.Organisation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(ConfidentialTest.class)
public class OrganisationIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    private Organisation createOrg(OrganisationsApi organisationsApi) {
        Organisation organisation = new Organisation();
        organisation.setName("testname");
        organisation.setLocation("testlocation");
        organisation.setLocation("testlink");
        organisation.setEmail("test@email.com");
        organisation.setDescription("This is the test description.");

        Organisation registeredOrganisation = organisationsApi.createOrganisation(organisation);
        return registeredOrganisation;
    }

    /**
     * Tests that a user can create an organisation and it will not be approved right away
     */
    @Test
    public void testCreateNewOrganisation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClient);

        Organisation registeredOrganisation = createOrg(organisationsApi);

        assertTrue(!registeredOrganisation.isApproved());
    }

    /**
     * Tests that you cannot create an organisation if another organisation already exists with the same name
     */
    @Test
    public void testCreateDuplicateOrganisation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClient);
        Organisation registeredOrganisation = createOrg(organisationsApi);

        boolean throwsError = false;
        try {
            Organisation duplicateOrganisation = createOrg(organisationsApi);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to register an organisation with a duplicate toolname.");
        }
    }


}
