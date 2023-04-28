package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.NOT_FOUND_ORCID_USER;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ORCID_USER_3;
import static io.dockstore.webservice.helpers.ORCIDHelper.getPutCodeFromLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.OrcidAuthorInformation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit5.HoverflyExtension;
import io.specto.hoverfly.junit5.api.HoverflyCore;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Optional;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag(NonConfidentialTest.NAME)
@ExtendWith(HoverflyExtension.class)
@HoverflyCore(mode = HoverflyMode.SIMULATE)
class ORCIDHelperTest {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    protected static TestingPostgres testingPostgres;

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    void exportEntry(Hoverfly hoverfly) throws JAXBException, IOException, DatatypeConfigurationException, URISyntaxException, InterruptedException {
        // This simulation assumes that the work that's trying to be created does not exist on orcid yet
        hoverfly.simulate(ORCID_SIMULATION_SOURCE);
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
        assertEquals(HttpStatus.SC_CREATED, response.statusCode());
        assertEquals("", response.body());
        String putCode = getPutCodeFromLocation(response);
        response = ORCIDHelper.postWorkString(id, orcidWorkString, token);
        assertEquals(HttpStatus.SC_CONFLICT, response.statusCode());
        assertTrue(
            response.body().contains("409 Conflict: You have already added this activity (matched by external identifiers), please see element with put-code " + putCode + ". If you are trying to edit the item, please use PUT instead of POST."));
        orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, putCode);
        response = ORCIDHelper.postWorkString(id, orcidWorkString, token);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
        assertTrue(response.body().contains("400 Bad Request: Put-code is included when not expected. When posting new activities, the put code should be omitted."));
        response = ORCIDHelper.putWorkString(id, orcidWorkString, token, putCode);
        assertEquals(HttpStatus.SC_OK, response.statusCode());
        assertTrue(response.body().contains("work:work put-code=\"" + putCode + "\" "));
        response = ORCIDHelper.getAllWorks(id, token);
        assertEquals(HttpStatus.SC_OK, response.statusCode());
    }

    @Test
    void testOrcidAuthor(Hoverfly hoverfly) throws URISyntaxException, IOException, InterruptedException {
        // This simulation assumes that the work that's trying to be created does not exist on orcid yet
        hoverfly.simulate(ORCID_SIMULATION_SOURCE);
        Optional<String> accessToken = ORCIDHelper.getOrcidAccessToken();
        assertTrue(accessToken.isPresent());

        HttpResponse<String> response = ORCIDHelper.getRecordDetails(ORCID_USER_3, accessToken.get());
        assertEquals(HttpStatus.SC_OK, response.statusCode());

        Optional<OrcidAuthorInformation> orcidAuthorInformation = ORCIDHelper.getOrcidAuthorInformation(ORCID_USER_3, accessToken.get());
        assertTrue(orcidAuthorInformation.isPresent(), "Should be able to get Orcid Author information");
        assertNotNull(orcidAuthorInformation.get().getOrcid());
        assertNotNull(orcidAuthorInformation.get().getName());
        assertNotNull(orcidAuthorInformation.get().getEmail());
        assertNotNull(orcidAuthorInformation.get().getAffiliation());
        assertNotNull(orcidAuthorInformation.get().getRole());

        response = ORCIDHelper.getRecordDetails(NOT_FOUND_ORCID_USER, accessToken.get());
        assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());

        orcidAuthorInformation = ORCIDHelper.getOrcidAuthorInformation(NOT_FOUND_ORCID_USER, accessToken.get());
        assertTrue(orcidAuthorInformation.isEmpty(), "An ORCID author that doesn't exist should not have ORCID info");
    }
}
