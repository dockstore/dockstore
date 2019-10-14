package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ZenodoHelperTest {

    @Test
    public void testcreateWorkflowTrsUrl() {
        final Workflow workflow = new BioWorkflow();

        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setOrganization("DataBiosphere");
        workflow.setRepository("topmed-workflows");
        workflow.setWorkflowName("UM_variant_caller_wdl");
        workflow.setDescriptorType(DescriptorLanguage.WDL);

        workflowVersion.setWorkflowPath("topmed_freeze3_calling.wdl");
        workflowVersion.setName("1.32.0");

        String trsUrl = ZenodoHelper.createWorkflowTrsUrl(workflow, workflowVersion, "https://dockstore.org/api/api/ga4gh/v2/tools/");
        Assert.assertEquals("https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere"
                + "%2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl", trsUrl);
    }

    @Test
    public void extractDoiFromDoiUrl() {
        String doiUrl = "https://doi.org/10.5072/zenodo.372767";
        String doi = ZenodoHelper.extractDoiFromDoiUrl(doiUrl);
        Assert.assertEquals("10.5072/zenodo.372767", doi);
    }

    @Test
    public void extractDoiFromBadDoiUrl() {
        String doiUrl = "https://doi.org/blah/10.5072/zenodo.372767";
        String doi = ZenodoHelper.extractDoiFromDoiUrl(doiUrl);
        Assert.assertNotEquals("10.5072/zenodo.372767", doi);
    }

    @Test
    public void checkAliasCreationFromDoiWithInvalidPrefix() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);
        String doi = "drs:10.5072/zenodo.372767";
        boolean throwsError = false;
        try {
            String formattedDoi = ZenodoHelper.createAliasUsingDoi(doi, user);
        } catch (CustomWebApplicationException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Non admin or curator was able to create an alias with an invalid prefix.");
        }
    }

    @Test
    public void checkAdminAliasCreationFromDoiWithInvalidPrefix() {
        User user = new User();
        user.setIsAdmin(true);
        user.setCurator(false);
        String doi = "drs:10.5072/zenodo.372767";
        boolean throwsError = false;
        try {
            String formattedDoi = ZenodoHelper.createAliasUsingDoi(doi, user);
        } catch (CustomWebApplicationException ex) {
            throwsError = true;
        }

        if (throwsError) {
            fail("Admin was not able to create an alias with an invalid prefix.");
        }
    }

    @Test
    public void checkNonAdminAliasCreationFromValidDoi() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);
        String doi = "10.5072/zenodo.372767";
        boolean throwsError = false;
        try {
            String formattedDoi = ZenodoHelper.createAliasUsingDoi(doi, user);
        } catch (CustomWebApplicationException ex) {
            throwsError = true;
        }

        if (throwsError) {
            fail("Was not able to create an alias with a valid prefix.");
        }
    }

}
