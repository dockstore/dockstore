package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ORCID_USER_3;
import static io.dockstore.webservice.helpers.ORCIDHelper.getPutCodeFromLocation;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Optional;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(NonConfidentialTest.class)
public class ORCIDHelperTest {

    /**
     * This simulation assumes that the work that's trying to be created does not exist on orcid yet
     */
    @ClassRule
    public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(ORCID_SIMULATION_SOURCE);

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    protected static TestingPostgres testingPostgres;

    @BeforeClass
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    public void exportEntry() throws JAXBException, IOException, DatatypeConfigurationException, URISyntaxException, InterruptedException {
        Workflow entry = new BioWorkflow();
        entry.setSourceControl(SourceControl.GITHUB);
        entry.setOrganization("dockstore");
        entry.setRepository("dockstore-ui2");
        entry.setWorkflowName("test");
        WorkflowVersion version = new WorkflowVersion();
        version.setParent(entry);
        version.setName("fakeVersionName");
        // Gary's public ORCID iD
        String id = "0000-0001-8365-0487";
        version.setLastModified(new Date());
        version.setDoiURL("https://doi.org/10.1038/s41586-020-1969-63");
        String token = "fakeToken";
        Optional<Version> optionalVersion = Optional.of(version);
        String orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, null);
        HttpResponse<String> response = ORCIDHelper.postWorkString(id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_CREATED, response.statusCode());
        Assert.assertEquals("", response.body());
        String putCode = getPutCodeFromLocation(response);
        response = ORCIDHelper.postWorkString(id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_CONFLICT, response.statusCode());
        Assert.assertTrue(response.body().contains("409 Conflict: You have already added this activity (matched by external identifiers), please see element with put-code " + putCode + ". If you are trying to edit the item, please use PUT instead of POST."));
        orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, putCode);
        response = ORCIDHelper.postWorkString(id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
        Assert.assertTrue(response.body().contains("400 Bad Request: Put-code is included when not expected. When posting new activities, the put code should be omitted."));
        response = ORCIDHelper.putWorkString(id, orcidWorkString, token, putCode);
        Assert.assertEquals(HttpStatus.SC_OK, response.statusCode());
        Assert.assertTrue(response.body().contains("work:work put-code=\"" + putCode + "\" "));
        response = ORCIDHelper.getAllWorks(id, token);
        Assert.assertEquals(HttpStatus.SC_OK, response.statusCode());
    }

    @Test
    public void testOrcidAuthor() throws URISyntaxException, IOException, InterruptedException {
        String id = ORCID_USER_3;
        Optional<String> accessToken = ORCIDHelper.getOrcidAccessToken();
        Assert.assertTrue(accessToken.isPresent());

        HttpResponse<String> response = ORCIDHelper.getPerson(id, accessToken.get());
        Assert.assertEquals(HttpStatus.SC_OK, response.statusCode());

        response = ORCIDHelper.getAllEmployments(id, accessToken.get());
        Assert.assertEquals(HttpStatus.SC_OK, response.statusCode());

        try {
            OrcidAuthor orcidAuthor = ORCIDHelper.createOrcidAuthor(id, accessToken.get());
            Assert.assertNotNull(orcidAuthor.getName());
            Assert.assertNotNull(orcidAuthor.getEmail());
            Assert.assertNotNull(orcidAuthor.getAffiliation());
            Assert.assertNotNull(orcidAuthor.getRole());
        } catch (URISyntaxException | IOException | InterruptedException | JAXBException ex) {
            Assert.fail("Should be able to create an Orcid Author");
        }
    }
}
