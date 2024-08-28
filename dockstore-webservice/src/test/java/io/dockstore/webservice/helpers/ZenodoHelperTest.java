package io.dockstore.webservice.helpers;

import static io.dockstore.webservice.core.Doi.getDoiBasedOnOrderOfPrecedence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.FixtureUtility;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.Doi.DoiType;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.ZenodoHelper.TagAndDoi;
import io.swagger.zenodo.client.ApiClient;
import io.swagger.zenodo.client.api.PreviewApi;
import io.swagger.zenodo.client.model.Author;
import io.swagger.zenodo.client.model.DepositMetadata;
import io.swagger.zenodo.client.model.SearchResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZenodoHelperTest {

    @Test
    void testBasicFunctionality() {
        ApiClient zenodoClient = new ApiClient();
        String zenodoUrlApi = "https://sandbox.zenodo.org/api";
        zenodoClient.setBasePath(zenodoUrlApi);
        PreviewApi previewApi = new PreviewApi(zenodoClient);
        final Map map = (Map) previewApi.listLicenses();
        // this is just a basic sanity check, the licenses api is one of the apis that does not require an access token, but it returns what
        // looks like an elasticsearch object, this does not match the documentation. Ironically, we have this too for search. 
        assertFalse(map.isEmpty());
    }

    @Test
    void testAuthorHashSetSanity() {
        // this tests that the zenodo classes used in zenodo helper have sane hashcodes and equals methods
        final String awesomeUniversity = "awesomeUniversity";
        final String someGuy = "Some guy";
        Author author1 = new Author();
        author1.setAffiliation("awesomeUniversity");
        author1.setName("Some guy");
        Author author2 = new Author();
        author2.setAffiliation("mediocreUniversity");
        author2.setName("Some other guy");
        Author author3 = new Author();
        author3.setAffiliation(awesomeUniversity);
        author3.setName(someGuy);
        Set<Author> authorSet = new HashSet<>();
        authorSet.add(author1);
        authorSet.add(author2);
        authorSet.add(author3);
        assertEquals(2, authorSet.size());
        Author oAuthor1 = new Author();
        oAuthor1.setOrcid("xxx-xxxx-1234-1234");
        String orcid = "XXX-xxx-4321-4321";
        Author oAuthor2 = new Author();
        oAuthor2.setOrcid(orcid);
        Author oAuthor3 = new Author();
        oAuthor3.setOrcid(orcid);
        authorSet.add(oAuthor1);
        authorSet.add(oAuthor2);
        authorSet.add(oAuthor3);
        assertEquals(4, authorSet.size());
    }

    @Test
    void testcreateWorkflowTrsUrl() {
        final Workflow workflow = new BioWorkflow();

        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setOrganization("DataBiosphere");
        workflow.setRepository("topmed-workflows");
        workflow.setWorkflowName("UM_variant_caller_wdl");
        workflow.setDescriptorType(DescriptorLanguage.WDL);

        workflowVersion.setWorkflowPath("topmed_freeze3_calling.wdl");
        workflowVersion.setName("1.32.0");

        DockstoreWebserviceConfiguration config = createDockstoreConfiguration();
        ZenodoHelper.initConfig(config);
        String trsUrl = ZenodoHelper.createWorkflowTrsUrl(workflow, workflowVersion);
        assertEquals("https://dockstore.org/api/ga4gh/trs/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere"
                + "%2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl", trsUrl);
    }

    @Test
    void checkAliasCreationFromDoiWithInvalidPrefix() {
        String doi = "drs:10.5072/zenodo.372767";
        try {
            ZenodoHelper.createAliasUsingDoi(doi);
            fail("Was able to create an alias with an invalid prefix.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Please create aliases without these prefixes"));
        }
    }

    @Test
    void checkCreationFromValidDoi() {
        String doi = "10.5072/zenodo.372767";
        ZenodoHelper.createAliasUsingDoi(doi);
    }

    @Test
    void testMetadataCreatorWithNoAuthors() {
        final DepositMetadata depositMetadata = new DepositMetadata();
        final BioWorkflow bioWorkflow = new BioWorkflow();
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        bioWorkflow.getWorkflowVersions().add(workflowVersion);
        try {
            ZenodoHelper.setMetadataCreator(depositMetadata, bioWorkflow, workflowVersion);
            fail("Should have failed");
        } catch (CustomWebApplicationException ex) {
            assertEquals(ZenodoHelper.AT_LEAST_ONE_AUTHOR_IS_REQUIRED_TO_PUBLISH_TO_ZENODO, ex.getMessage());
        }
    }

    @Test
    void testMetadataCreateWithOneAuthor() {
        final DepositMetadata depositMetadata = new DepositMetadata();
        final BioWorkflow bioWorkflow = new BioWorkflow();
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        final io.dockstore.webservice.core.Author author = new io.dockstore.webservice.core.Author();
        final String joeBlow = "Joe Blow";
        author.setName(joeBlow);
        workflowVersion.addAuthor(author);
        bioWorkflow.getWorkflowVersions().add(workflowVersion);
        bioWorkflow.setActualDefaultVersion(workflowVersion);
        ZenodoHelper.setMetadataCreator(depositMetadata, bioWorkflow, workflowVersion);
        assertEquals(joeBlow, depositMetadata.getCreators().get(0).getName());
    }

    @Test
    void testExtractRecordIdFromDoi() {
        assertEquals("372767", ZenodoHelper.extractRecordIdFromDoi("10.5072/zenodo.372767"));
        assertEquals("372767", ZenodoHelper.extractRecordIdFromDoi("doi/10.5072/zenodo.372767"));
    }

    @Test
    void testSetMetadataCommunities() {
        final String dockstoreCommunityId = "dockstore-community";
        final DockstoreWebserviceConfiguration configuration = createDockstoreConfiguration();
        configuration.setDockstoreZenodoCommunityId(dockstoreCommunityId);
        ZenodoHelper.initConfig(configuration);
        DepositMetadata depositMetadata = new DepositMetadata();
        ZenodoHelper.setMetadataCommunities(depositMetadata);
        assertEquals(dockstoreCommunityId, depositMetadata.getCommunities().get(0).getIdentifier());
    }

    @Test
    void testDefaultDoiOrderOfPrecedence() {
        Map<DoiInitiator, Doi> dois = new HashMap<>();
        assertNull(getDoiBasedOnOrderOfPrecedence(dois));

        dois.put(DoiInitiator.DOCKSTORE, new Doi(DoiType.VERSION, DoiInitiator.DOCKSTORE, "foobar"));
        assertEquals(DoiInitiator.DOCKSTORE, getDoiBasedOnOrderOfPrecedence(dois).getInitiator());

        dois.put(DoiInitiator.GITHUB, new Doi(DoiType.VERSION, DoiInitiator.GITHUB, "foobar"));
        assertEquals(DoiInitiator.GITHUB, getDoiBasedOnOrderOfPrecedence(dois).getInitiator());

        dois.put(DoiInitiator.USER, new Doi(DoiType.VERSION, DoiInitiator.USER, "foobar"));
        assertEquals(DoiInitiator.USER, getDoiBasedOnOrderOfPrecedence(dois).getInitiator());
    }

    @Test
    void testIdFromZenodoDoi() {
        assertEquals("5104875", ZenodoHelper.idFromZenodoDoi("10.5281/zenodo.5104875"));
        assertNull(ZenodoHelper.idFromZenodoDoi(null));
        assertEquals("", ZenodoHelper.idFromZenodoDoi("notavaliddoi"));
    }

    @Test
    void testFindGitHubIntegrationDois() throws JsonProcessingException {
        final SearchResult searchResult = deserialize(FixtureUtility.fixture("fixtures/zenodoListRecords.json"));
        final List<ZenodoHelper.ConceptAndDoi> dois = ZenodoHelper.findGitHubIntegrationDois(searchResult.getHits().getHits(), "coverbeck/cwlviewer");
        assertEquals(List.of(new ZenodoHelper.ConceptAndDoi("10.5281/zenodo.11094520", "10.5281/zenodo.11099749")), dois);
    }


    @Test
    void testTaggedVersions() throws JsonProcessingException {
        final SearchResult searchResult = deserialize(FixtureUtility.fixture("fixtures/zenodoVersions.json"));
        final List<TagAndDoi> taggedVersions = ZenodoHelper.findTaggedVersions(searchResult.getHits().getHits(), "coverbeck/cwlviewer");
        final List<TagAndDoi> expected = List.of(
                new TagAndDoi("FourthTestTag", "10.5281/zenodo.11099749"),
                new TagAndDoi("ThirdTestTag", "10.5281/zenodo.11095575"),
                new TagAndDoi("SecondTestTag", "10.5281/zenodo.11095507"),
                new TagAndDoi("testtag", "10.5281/zenodo.11094521"));
        assertEquals(expected, taggedVersions);
    }

    @Test
    void testTagFromRelatedIdentifier() {
        assertEquals(Optional.empty(), ZenodoHelper.tagFromRelatedIdentifier("dockstore/dockstore-cli", "https://github.com/dockstore/dockstore-cli/mytag"));
        assertEquals(Optional.empty(), ZenodoHelper.tagFromRelatedIdentifier("dockstore/dockstore-cli", "nonsense-icalstring"));
        assertEquals("mytag", ZenodoHelper.tagFromRelatedIdentifier("dockstore/dockstore-cli", "https://github.com/dockstore/dockstore-cli/tree/mytag").get());
        assertEquals("1.16", ZenodoHelper.tagFromRelatedIdentifier("dockstore/dockstore-cli", "https://github.com/dockstore/dockstore-cli/tree/1.16").get());
    }


    private DockstoreWebserviceConfiguration createDockstoreConfiguration() {
        final DockstoreWebserviceConfiguration config = new DockstoreWebserviceConfiguration();
        config.getExternalConfig().setBasePath("/api/");
        config.getExternalConfig().setHostname("dockstore.org");
        config.getExternalConfig().setScheme("https");
        return config;
    }
    private static SearchResult deserialize(String fixture) throws JsonProcessingException {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final SearchResult searchResult = objectMapper.readValue(fixture, SearchResult.class);
        return searchResult;
    }

}
