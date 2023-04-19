package io.dockstore.webservice.helpers;

import static io.swagger.api.impl.ToolsImplCommon.WORKFLOW_PREFIX;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.AliasableResourceInterface;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.zenodo.client.ApiClient;
import io.swagger.zenodo.client.ApiException;
import io.swagger.zenodo.client.api.ActionsApi;
import io.swagger.zenodo.client.api.DepositsApi;
import io.swagger.zenodo.client.api.FilesApi;
import io.swagger.zenodo.client.model.Author;
import io.swagger.zenodo.client.model.Community;
import io.swagger.zenodo.client.model.Deposit;
import io.swagger.zenodo.client.model.DepositMetadata;
import io.swagger.zenodo.client.model.NestedDepositMetadata;
import io.swagger.zenodo.client.model.RelatedIdentifier;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZenodoHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ZenodoHelper.class);

    private ZenodoHelper() {
    }

    /**
     * Register a Zenodo DOI for the workflow version
     * @param zenodoClient Client for interacting with Zenodo server
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param workflowUrl Dockstore workflow URL (e.g. https://dockstore.org/workflows/github.com/DataBiosphere/topmed-workflows/UM_variant_caller_wdl)
     * @param dockstoreGA4GHBaseUrl The baseURL for GA4GH tools endpoint (e.g. "http://localhost:8080/api/api/ga4gh/v2/tools/")
     * @param dockstoreUrl URL for Dockstore (e.g. https://dockstore.org)
     * @param entryVersionHelper provides interface for retrieving the files of versions
     */
    public static ZenodoDoiResult registerZenodoDOI(ApiClient zenodoClient, Workflow workflow,
            WorkflowVersion workflowVersion, String workflowUrl, String dockstoreGA4GHBaseUrl,
            String dockstoreUrl, EntryVersionHelper entryVersionHelper) {
        DepositsApi depositApi = new DepositsApi(zenodoClient);
        ActionsApi actionsApi = new ActionsApi(zenodoClient);
        Deposit deposit = new Deposit();
        Deposit returnDeposit;
        checkForExistingDOIForWorkflowVersion(workflowVersion);
        Optional<String> existingWorkflowVersionDOIURL = getAnExistingDOIForWorkflow(workflow);

        int depositionID;
        DepositMetadata depositMetadata;
        String doiAlias;
        if (existingWorkflowVersionDOIURL.isEmpty()) {
            try {
                // No DOI has been assigned to any version of the workflow yet
                // So create a new deposit which will enable creation of a new
                // concept DOI and new version DOI
                returnDeposit = depositApi.createDeposit(deposit);
                depositionID = returnDeposit.getId();
                depositMetadata = returnDeposit.getMetadata();
                // Set the attribute that will reserve a DOI before publishing
                fillInMetadata(depositMetadata, workflow, workflowVersion);
                depositMetadata.prereserveDoi(true);
                // Put the deposit on Zenodo; the returned deposit will contain
                // the reserved DOI which we can use to create a workflow alias
                // Later on we will update the Zenodo deposit (put the deposit on
                // Zenodo again  in the call to putDepositionOnZenodo) so it contains the workflow version alias
                // constructed with the DOI
                Deposit newDeposit = putDepositionOnZenodo(depositApi, depositMetadata, depositionID);
                depositMetadata.prereserveDoi(false);
                // Retrieve the DOI so we can use it to create a Dockstore alias
                // to the workflow; we will add that alias as a Zenodo related identifier
                Map<String, String> doiMap = (Map<String, String>)newDeposit.getMetadata().getPrereserveDoi();
                Map.Entry<String, String> doiEntry = doiMap.entrySet().iterator().next();
                String doi = doiEntry.getValue();
                doiAlias = createAliasUsingDoi(doi);
                setMetadataRelatedIdentifiers(depositMetadata, dockstoreGA4GHBaseUrl,
                        dockstoreUrl, workflowUrl, workflow, workflowVersion, doiAlias);
            } catch (ApiException e) {
                LOG.error("Could not create deposition on Zenodo. Error is " + e.getMessage(), e);
                throw new CustomWebApplicationException("Could not create deposition on Zenodo. "
                        + "Error is " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            String depositIdStr = existingWorkflowVersionDOIURL.get()
                    .substring(existingWorkflowVersionDOIURL.get().lastIndexOf(".") + 1).trim();
            int depositId = Integer.parseInt(depositIdStr);
            try {
                // A DOI was previously assigned to a workflow version so we will
                // use the ID associated with the workflow version DOI
                // to create a new workflow version DOI
                returnDeposit = actionsApi.newDepositVersion(depositId);
                // The response body of this action is NOT the new version deposit,
                // but the original resource. The new version deposition can be
                // accessed through the "latest_draft" under "links" in the response body.
                String depositURL = returnDeposit.getLinks().get("latest_draft");
                String depositionIDStr = depositURL.substring(depositURL.lastIndexOf("/") + 1).trim();
                // Get the deposit object for the new workflow version DOI
                depositionID = Integer.parseInt(depositionIDStr);
                returnDeposit = depositApi.getDeposit(depositionID);
                depositMetadata = returnDeposit.getMetadata();
                // Retrieve the DOI so we can use it to create a Dockstore alias
                // to the workflow; we will add that alias as a Zenodo related identifier
                String doi = depositMetadata.getDoi();
                doiAlias = createAliasUsingDoi(doi);
                setMetadataRelatedIdentifiers(depositMetadata,  dockstoreGA4GHBaseUrl,
                        dockstoreUrl, workflowUrl, workflow, workflowVersion, doiAlias);
                fillInMetadata(depositMetadata, workflow, workflowVersion);
            } catch (ApiException e) {
                LOG.error("Could not create new deposition version on Zenodo. Error is " + e.getMessage(), e);
                if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
                    // Another user in the same organization already requested a DOI for a workflow version and created the workflow concept DOI.
                    // We are unable to create a new deposition version using the current user's Zenodo credentials because they don't have permission to create a deposition version for the original concept DOI.
                    // The workaround is for the user who created the concept DOI to request DOI's for other versions whenever it's needed by users from the same organization.
                    // This will hopefully be revisited later when Zenodo implements a feature where a deposit can be shared among users.
                    String errorMessage = String.format(
                            "Could not create new deposition version on Zenodo because you do not have permission to create a deposition version for DOI %s. "
                                    + "Please ask the person who created DOI %s to request a DOI for workflow version %s on Dockstore.",
                            workflow.getConceptDoi(), workflow.getConceptDoi(), workflowVersion.getName());
                    throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
                } else {
                    throw new CustomWebApplicationException("Could not create new deposition version on Zenodo."
                        + " Error is " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
                }
            }
        }

        provisionWorkflowVersionUploadFiles(zenodoClient, returnDeposit, depositionID,
                workflow, workflowVersion, entryVersionHelper);

        putDepositionOnZenodo(depositApi, depositMetadata, depositionID);

        Deposit publishedDeposit = publishDepositOnZenodo(actionsApi, depositionID);

        String conceptDoiUrl = publishedDeposit.getLinks().get("conceptdoi");

        String conceptDoi = extractDoiFromDoiUrl(conceptDoiUrl);

        return new ZenodoDoiResult(doiAlias, publishedDeposit.getMetadata().getDoi(), conceptDoi);
    }

    /**
     * extract a digital object identifier (DOI) from a DOI target URL
     * @param doiUrl digital object identifier
     * @return the DOI as a string
     */
    protected static String extractDoiFromDoiUrl(String doiUrl) {
        // Remove the 'https://doi.org/' etc. prefix from the DOI
        // e.g. https://doi.org/10.5072/zenodo.372767
        String doi = doiUrl;
        try {
            URI uri = new URI(doiUrl);
            doi = StringUtils.stripStart(uri.getPath(), "/");

        } catch (URISyntaxException e) {
            LOG.error("Could not extract DOI. Error is " + e.getMessage(), e);
        }
        return doi;
    }

    /**
     * Create a workflow alias that uses a digital object identifier
     * and make sure the alias is valid
     * If it is not acceptable then an exception is generated
     * in which case a deposition resource may be left on Zenodo
     * that the user will have to clean up manually
     * @param doi digital object identifier
     * @return the alias as a string
     */
    protected static String createAliasUsingDoi(String doi) {
        // Replace forward slashes so we can use the DOI in an alias
        String doiReformattedAlias = doi.replaceAll("/", "-");
        // Make sure the alias is valid
        // If it is not acceptable then an exception is generated
        // We allow aliases with Zenodo format to be created because
        // we are going use that alias format in a Related Identifier which will
        // be part of a DOI entry that we create on the Zenodo site
        AliasableResourceInterface.checkAliasFormat(Collections.singleton(doiReformattedAlias), false);
        return doiReformattedAlias;
    }

    /**
     * Add the workflow labels as keywords to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     */
    private static void setMetadataKeywords(DepositMetadata depositMetadata, Workflow workflow) {
        // Use the Dockstore workflow labels as Zenodo free form keywords for this deposition.
        List<String> labelList = workflow.getLabels().stream().map(Label::getValue).collect(Collectors.toList());
        depositMetadata.setKeywords(labelList);
    }

    /**
     * Add the workflow labels as keywords to the deposition metadata
     * @param relatedIdentifierList a list of URLs which will be uploaded to Zenodo as related identifiers
     * @param workflowUri a URI to a workflow
     */
    private static void addUriToRelatedIdentifierList(List<RelatedIdentifier> relatedIdentifierList, String workflowUri) {
        RelatedIdentifier aliasRelatedIdentifier = new RelatedIdentifier();
        aliasRelatedIdentifier.setIdentifier(workflowUri);
        aliasRelatedIdentifier.setRelation(RelatedIdentifier.RelationEnum.ISIDENTICALTO);
        relatedIdentifierList.add(aliasRelatedIdentifier);
    }

    /**
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param dockstoreGA4GHBaseUrl The baseURL for GA4GH tools endpoint (e.g. "http://localhost:8080/api/api/ga4gh/v2/tools/")
     * @return TRS URL to workflow (e.g. https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere
     *     %2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl)
     */
    protected static String createWorkflowTrsUrl(Workflow workflow, WorkflowVersion workflowVersion, String dockstoreGA4GHBaseUrl) {
        final String sourceControlPath = workflow.getWorkflowPath();
        final String workflowVersionPrimaryDescriptorPath = WORKFLOW_PREFIX + "/" + sourceControlPath;
        final String workflowVersionPrimaryDescriptorPathPlainText;
        try {
            workflowVersionPrimaryDescriptorPathPlainText =
                    ToolsImplCommon.getUrl(workflowVersionPrimaryDescriptorPath, dockstoreGA4GHBaseUrl);
        } catch (UnsupportedEncodingException e) {
            LOG.error("Could not create Zenodo related identifier." + " Error is " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not create Zenodo related identifier",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        final String workflowVersionType = workflow.getDescriptorType().toString();
        final String workflowDescriptorName = workflowVersion.getWorkflowPath();
        Path p = Paths.get(workflowDescriptorName);
        final String descriptorFile = p.getFileName().toString();
        final String versionOfWorkflow = workflowVersion.getName();
        return workflowVersionPrimaryDescriptorPathPlainText + "/versions/" + versionOfWorkflow
                + "/PLAIN-" + workflowVersionType + "/descriptor/" + descriptorFile;
    }

    /**
     * Add the workflow aliases as related identifiers to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param dockstoreGA4GHBaseUrl The baseURL for GA4GH tools endpoint (e.g. "http://localhost:8080/api/api/ga4gh/v2/tools/")
     * @param dockstoreUrl URL for Dockstore (e.g. https://dockstore.org)
     * @param workflowUrl Dockstore workflow URL (e.g. https://dockstore.org/workflows/github.com/DataBiosphere/topmed-workflows/UM_variant_caller_wdl)
     * @param workflow workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param doiAlias workflow alias constructed using a DOI
     */
    private static void setMetadataRelatedIdentifiers(DepositMetadata depositMetadata,
            String dockstoreGA4GHBaseUrl, String dockstoreUrl, String workflowUrl,
            Workflow workflow, WorkflowVersion workflowVersion, String doiAlias) {

        List<RelatedIdentifier> relatedIdentifierList = new ArrayList<>();

        // Add the workflow version alias as a related identifier on Zenodo
        // E.g https://dockstore.org/aliases/workflow-versions/10.5281-zenodo.2630727
        final String aliasUrl = dockstoreUrl + "/aliases/workflow-versions/" + doiAlias;
        addUriToRelatedIdentifierList(relatedIdentifierList, aliasUrl);

        // Add the UI2 link to the workflow to Zenodo as a related identifier
        // E.g https://dockstore.org/workflows/github.com/DataBiosphere/topmed-workflows/UM_variant_caller_wdl:1.32.0
        final String versionOfWorkflow = workflowVersion.getName();
        final String workflowVersionURL = workflowUrl + ":" + versionOfWorkflow;
        addUriToRelatedIdentifierList(relatedIdentifierList, workflowVersionURL);

        // Add the workflow Task Registry Service (TRS) URL to Zenodo as a related identifier
        // E.g. https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere
        // %2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl
        final String workflowVersionTrsUrl = createWorkflowTrsUrl(workflow, workflowVersion, dockstoreGA4GHBaseUrl);
        addUriToRelatedIdentifierList(relatedIdentifierList, workflowVersionTrsUrl);

        depositMetadata.setRelatedIdentifiers(relatedIdentifierList);
    }

    /**
     * Add the workflow author as creator to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     */
    private static void setMetadataCreator(DepositMetadata depositMetadata, Workflow workflow) {
        // TODO: remove usage of getAuthor() below in https://github.com/dockstore/dockstore/issues/5437
        String wfAuthor = workflow.getAuthor();
        String authorStr = (wfAuthor == null || wfAuthor.isEmpty()) ? "Author not specified" : workflow.getAuthor();
        Author author = new Author();
        author.setName(authorStr);
        depositMetadata.setCreators(Collections.singletonList(author));
    }

    /**
     * Add a communities list to the deposition metadata even if it is empty
     * @param depositMetadata Metadata for the workflow version
     */
    private static void setMetadataCommunities(DepositMetadata depositMetadata) {
        // A communities entry must not be null, but it can be a null
        // List for Zenodo
        List<Community> communities = depositMetadata.getCommunities();
        if (communities == null || communities.isEmpty()) {
            List<Community> myList = new ArrayList<>();
            depositMetadata.setCommunities(myList);
        } else if (communities.size() == 1 && communities.get(0).getId() == null) {
            // Sometimes the list of communities contains one object
            // with a null id when Zenodo copies the metadata.
            // This will cause the call to publish to fail, so clear
            // the list of communities in this case
            List<Community> myList = new ArrayList<>();
            depositMetadata.setCommunities(myList);
        }
    }

    /**
     * Add the workflow version information to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     */
    private static void fillInMetadata(DepositMetadata depositMetadata,
            Workflow workflow, WorkflowVersion workflowVersion) {
        // add some metadata to the deposition that will be published to Zenodo
        depositMetadata.setTitle(workflow.getWorkflowPath());
        // The Zenodo deposit type for Dockstore will always be SOFTWARE
        depositMetadata.setUploadType(DepositMetadata.UploadTypeEnum.SOFTWARE);
        // A metadata description is required for Zenodo
        String description = workflow.getDescription();
        // The Zenodo API requires at description of at least three characters
        String descriptionStr = (description == null || description.isEmpty()) ? "No description specified" : workflow.getDescription();
        depositMetadata.setDescription(descriptionStr);

        // We will set the Zenodo workflow version publication date to the date of the DOI issuance
        depositMetadata.setPublicationDate(ZonedDateTime.now().toLocalDate().toString());

        depositMetadata.setVersion(workflowVersion.getName());

        setMetadataKeywords(depositMetadata, workflow);

        setMetadataCreator(depositMetadata, workflow);

        setMetadataCommunities(depositMetadata);
    }

    /**
     * Provision files to upload to Zenodo
     * @param zendoClient Zenodo api client
     * @param returnDeposit Deposit object for the new version
     * @param depositionID ID of Zendo deposit to which files will be attached
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param entryVersionHelper code for interacting with the files of versions, we use zip file creation methods
     */
    private static void provisionWorkflowVersionUploadFiles(ApiClient zendoClient, Deposit returnDeposit,
            int depositionID, Workflow workflow, WorkflowVersion workflowVersion, EntryVersionHelper entryVersionHelper) {
        // Creating a new version copies the files from the previous version
        // We want to delete these since we will upload a new set of files
        // if creating a completely new deposit this should not cause a problem
        FilesApi filesApi = new FilesApi(zendoClient);

        returnDeposit.getFiles().forEach(file -> {
            String fileIdStr = file.getId();
            filesApi.deleteFile(depositionID, fileIdStr);
        });



        // Add workflow version source files as a zip to the DOI upload deposit
        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
        if (sourceFiles == null || sourceFiles.size() == 0) {
            LOG.warn("No source files found to zip when creating DOI");
            throw new CustomWebApplicationException("No source files found to"
                    + " upload when creating DOI. Zenodo requires at lease one file"
                    + " to be uploaded in order to create a DOI.", HttpStatus.SC_BAD_REQUEST);
        } else {
            // Replace forward slashes so we can use the version in a file name
            String versionOfWorkflow = workflowVersion.getName().replaceAll("/", "-");
            // Replace forward slashes so we can use the workflow path in a file name
            String fileNameBase = workflow.getWorkflowPath().replaceAll("/", "-")
                    + "_" + versionOfWorkflow;
            String fileSuffix = ".zip";
            String fileName = fileNameBase + fileSuffix;
            Path tempDirPath;
            try {
                tempDirPath = Files.createTempDirectory(null);
            } catch (IOException e) {
                LOG.error("Could not create Zenodo temp upload directory." + " Error is " + e.getMessage(), e);
                throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            String zipFilePathName = tempDirPath.toString() + "/" + fileName;

            try (OutputStream outputStream = new FileOutputStream(zipFilePathName)) {
                entryVersionHelper.writeStreamAsZip(sourceFiles, outputStream, Paths.get(zipFilePathName));
            } catch (IOException fne) {
                // Delete the temporary directory
                FileUtils.deleteQuietly(tempDirPath.toFile());
                LOG.error(
                    "Could not create file " + zipFilePathName + " outputstream for DOI zip file for upload to Zenodo." + " Error is " + fne
                        .getMessage(), fne);
                throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            File zipFile = new File(zipFilePathName);

            try {
                filesApi.createFile(depositionID, zipFile, fileName);
            } catch (ApiException e) {
                LOG.error("Could not create files for new version on Zenodo. Error is " + e.getMessage(), e);
                throw new CustomWebApplicationException("Could not create files for new version on Zenodo."
                        + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                // Delete the zip file in the temporary directory
                FileUtils.deleteQuietly(zipFile);
                // Delete the temporary directory
                FileUtils.deleteQuietly(tempDirPath.toFile());
            }
        }
    }


    /**
     * Check if a Zenodo DOI already exists for the workflow version
     * @param workflowVersion workflow version
     */
    private static void checkForExistingDOIForWorkflowVersion(WorkflowVersion workflowVersion) {
        String workflowVersionDoiURL = workflowVersion.getDoiURL();
        if (workflowVersionDoiURL != null && !workflowVersionDoiURL.isEmpty()) {
            LOG.error("Workflow version " + workflowVersion.getName() + " already has DOI " + workflowVersionDoiURL
                    + ". Dockstore can only create one DOI per version.");
            throw new CustomWebApplicationException("Workflow version " + workflowVersion.getName() + " already has DOI "
                    + workflowVersionDoiURL + ". Dockstore can only create one DOI per version.", HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Get an existing Zenodo DOI for the workflow if one exists
     * otherwise return null
     * @param workflow workflow
     * @return the DOI for a workflow
     */
    private static Optional<String> getAnExistingDOIForWorkflow(Workflow workflow) {
        // Find out if this workflow already has at least one
        // version that has been assigned a DOI
        // If a version DOI exists, we will create another version DOI
        // instead of creating a new workflow concept DOI and version DOI
        // Get the ID of one of the workflow version DOIs
        // because Zenodo requires that we use it to create the next
        // workflow version DOI

        return workflow.getWorkflowVersions().stream()
                .map(Version::getDoiURL)
                .filter(doi -> !StringUtils.isEmpty(doi)).findAny();
    }

    /**
     * Put the deposit data on Zenodo
     * @param depositApi Zenodo API for working with depositions
     * @param depositMetadata Metadata for the workflow version
     * @param depositionID Zenodo's ID for the deposition
     * @return a copy of the deposit that was put on Zenodo
     */
    private static Deposit putDepositionOnZenodo(DepositsApi depositApi, DepositMetadata depositMetadata,
            int depositionID) {
        NestedDepositMetadata nestedDepositMetadata = new NestedDepositMetadata();
        nestedDepositMetadata.setMetadata(depositMetadata);
        Deposit deposit;
        try {
            deposit = depositApi.putDeposit(depositionID, nestedDepositMetadata);
        } catch (ApiException e) {
            LOG.error("Could not put deposition metadata on Zenodo. Error is " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not put deposition metadata on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return deposit;
    }

    /**
     * Publish the deposit on Zenodo
     * @param actionsApi Zenodo API for publishing deposits
     * @param depositionID Zenodo's ID for the deposition
     * @return a copy of the deposit that was published
     */
    private static Deposit publishDepositOnZenodo(ActionsApi actionsApi, int depositionID) {
        Deposit publishedDeposit;
        try {
            publishedDeposit = actionsApi.publishDeposit(depositionID);
        } catch (ApiException e) {
            LOG.error("Could not publish DOI on Zenodo. Error is " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not publish DOI on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return publishedDeposit;
    }

    public static final class ZenodoDoiResult {
        private final String doiAlias;
        private final String doiUrl;
        private final String conceptDoi;

        public ZenodoDoiResult(String doiAlias, String doiUrl, String conceptDoi) {
            this.doiAlias = doiAlias;
            this.doiUrl = doiUrl;
            this.conceptDoi = conceptDoi;
        }

        public String getDoiAlias() {
            return doiAlias;
        }

        public String getDoiUrl() {
            return doiUrl;
        }

        public String getConceptDoi() {
            return conceptDoi;
        }
    }

}
