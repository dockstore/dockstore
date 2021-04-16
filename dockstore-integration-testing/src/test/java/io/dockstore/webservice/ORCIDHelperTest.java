package io.dockstore.webservice;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Optional;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.dockstore.webservice.helpers.ORCIDHelper.getPutCodeFromLocation;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.response;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.badRequest;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers.contains;

@Category(NonConfidentialTest.class)
public class ORCIDHelperTest {

    private static final String BASE_URL = "https://api.sandbox.orcid.org/v3.0/";
    // Changing this requires changing the fixtures too
    private static final String PUT_CODE = "7777777";

    /**
     * This simulation assumes that the work that's trying to be created already exists
     */
    @ClassRule
    public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(dsl(
            service("https://api.sandbox.orcid.org")
                    .post("/v3.0/0000-0001-8365-0487/work").anyBody().willReturn(response().status(HttpStatus.SC_CREATED).andSetState("Work", "Created").header("Location", PUT_CODE))
                    .post("/v3.0/0000-0001-8365-0487/work").withState("Work", "Created").anyBody().willReturn(response().status(HttpStatus.SC_CONFLICT).body(fixture(
                    "fixtures/successfulPostOrcidWork.xml")))
                    .post("/v3.0/0000-0001-8365-0487/work").body(contains(PUT_CODE)).willReturn(badRequest().body(fixture("fixtures/putCodeOnPostOrcidWork.xml")))
                    .put("/v3.0/0000-0001-8365-0487/work/" + PUT_CODE).body(contains(PUT_CODE)).willReturn(success().body(fixture("fixtures/successfulPutOrcidWork.xml")))
    ));
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
        HttpResponse<String> response = ORCIDHelper.postWorkString(BASE_URL, id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_CREATED, response.statusCode());
        Assert.assertEquals("", response.body());
        String putCode = getPutCodeFromLocation(response);
        response = ORCIDHelper.postWorkString(BASE_URL, id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_CONFLICT, response.statusCode());
        Assert.assertTrue(response.body().contains("409 Conflict: You have already added this activity (matched by external identifiers), please see element with put-code " + putCode + ". If you are trying to edit the item, please use PUT instead of POST."));
        orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, putCode);
        response = ORCIDHelper.postWorkString(BASE_URL, id, orcidWorkString, token);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
        Assert.assertTrue(response.body().contains("400 Bad Request: Put-code is included when not expected. When posting new activities, the put code should be omitted."));
        response = ORCIDHelper.putWorkString(BASE_URL, id, orcidWorkString, token, putCode);
        Assert.assertEquals(HttpStatus.SC_OK, response.statusCode());
        Assert.assertTrue(response.body().contains("work:work put-code=\"" + putCode + "\" "));
    }
}
