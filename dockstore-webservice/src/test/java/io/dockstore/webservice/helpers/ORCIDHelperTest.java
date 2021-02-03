package io.dockstore.webservice.helpers;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;

public class ORCIDHelperTest {

    @Test
    public void exportEntry() throws JAXBException, IOException, DatatypeConfigurationException {
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
        version.setDoiURL("https://doi.org/10.1038/s41586-020-1969-6");
        Optional<Version> optionalVersion = Optional.of(version);
        String orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion);
        HttpResponse response = ORCIDHelper.postWorkString(id, orcidWorkString, "fakeToken");
        Assert.assertEquals("Should fail because of fake token", HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
    }
}