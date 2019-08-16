package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.WorkflowResource;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZenodoHelper {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowResource.class);

    private ZenodoHelper() {
    }

    /**
     * Register a Zenodo DOI for the workflow version
     * @param zenodoUrl URL of the Zenodo website, e.g. 'https://sandbox.zenodo.org' or 'https://zenodo.org'
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param entryVersionHelper code for interacting with the files of versions, we use zip file creation methods
     */
    public static void registerZenodoDOIForWorkflow(String zenodoUrl, String zenodoAccessToken, Workflow workflow,
            WorkflowVersion workflowVersion, EntryVersionHelper entryVersionHelper) {

        ApiClient zendoClient = new ApiClient();
        // for testing, either 'https://sandbox.zenodo.org/api' or 'https://zenodo.org/api' is the first parameter
        String zenodoUrlApi = zenodoUrl + "/api";
        zendoClient.setBasePath(zenodoUrlApi);
        zendoClient.setApiKey(zenodoAccessToken);

        DepositsApi depositApi = new DepositsApi(zendoClient);
        ActionsApi actionsApi = new ActionsApi(zendoClient);

        Deposit deposit = new Deposit();
        Deposit returnDeposit;

        checkForExistingDOIForWorkflowVersion(workflowVersion);

        Optional<String> existingWorkflowVersionDOIURL = getAnExistingDOIForWorkflow(workflow);

        int depositionID;
        DepositMetadata depositMetadata;

        if (existingWorkflowVersionDOIURL.isEmpty()) {
            try {
                // No DOI has been assigned to any version of the workflow yet
                // So create a new deposit which will enable creation of a new
                // concept DOI and new version DOI
                returnDeposit = depositApi.createDeposit(deposit);
                depositionID = returnDeposit.getId();
                depositMetadata = returnDeposit.getMetadata();

                fillInMetadata(depositMetadata, workflow, workflowVersion);

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
                // A DOI was assigned to a workflow version so we will
                // use the ID associated with the workflow version DOI
                // to create a new workflow version DOI
                returnDeposit = actionsApi.newDepositVersion(depositId);
                // The response body of this action is NOT the new version deposit,
                // but the original resource. The new version deposition can be
                // accessed through the "latest_draft" under "links" in the response body.
                Object links = returnDeposit.getLinks();
                String depositURL = (String)((LinkedHashMap)links).get("latest_draft");

                String depositionIDStr = depositURL.substring(depositURL.lastIndexOf("/") + 1).trim();
                // Get the deposit object for the new workflow version DOI
                depositionID = Integer.parseInt(depositionIDStr);
                returnDeposit = depositApi.getDeposit(depositionID);

                depositMetadata = returnDeposit.getMetadata();
                fillInMetadata(depositMetadata, workflow, workflowVersion);

            } catch (ApiException e) {
                LOG.error("Could not create new deposition version on Zenodo. Error is " + e.getMessage(), e);
                throw new CustomWebApplicationException("Could not create new deposition version on Zenodo."
                        + " Error is " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
        }

        provisionWorkflowVersionUploadFiles(zendoClient, returnDeposit, depositionID, workflow, workflowVersion, entryVersionHelper);

        putDepositionOnZenodo(depositApi, depositMetadata, depositionID);

        Deposit publishedDeposit = publishDepositOnZenodo(actionsApi, depositionID);

        workflowVersion.setDoiURL(publishedDeposit.getMetadata().getDoi());
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
     * Add the workflow aliases as related identifiers to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     */
    private static void setMetadataRelatedIdentifiers(DepositMetadata depositMetadata, Workflow workflow) {
        // Get the aliases for this workflow and add them to the deposit
        // The alias must be a format supported by Zenodo such as
        // DOI, Handle, ARK...URNs and URLs
        // See http://developers.zenodo.org/#representation 'related identifiers'
        List<RelatedIdentifier> aliasList = workflow.getAliases().keySet().stream()
                .map(s -> {
                    RelatedIdentifier relatedIdentifier = new RelatedIdentifier();
                    relatedIdentifier.setIdentifier(s);
                    relatedIdentifier.setRelation(RelatedIdentifier.RelationEnum.ISIDENTICALTO);
                    return relatedIdentifier;
                }).collect(Collectors.toList());

        depositMetadata.setRelatedIdentifiers(aliasList);
    }

    /**
     * Add the workflow author as creator to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     */
    private static void setMetadataCreator(DepositMetadata depositMetadata, Workflow workflow) {
        String wfAuthor = workflow.getAuthor();
        String authorStr = (wfAuthor == null || wfAuthor.isEmpty()) ? "unknown creator" : workflow.getAuthor();
        Author author = new Author();
        author.setName(authorStr);
        depositMetadata.setCreators(Collections.singletonList(author));
    }

    /**
     * Add a communites list to to the deposition metadata even if it is empty
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
    private static void fillInMetadata(DepositMetadata depositMetadata, Workflow workflow, WorkflowVersion workflowVersion) {
        // add some metadata to the deposition that will be published to Zenodo
        depositMetadata.setTitle(workflow.getWorkflowPath());
        // The Zenodo deposit type for Dockstore will always be SOFTWARE
        depositMetadata.setUploadType(DepositMetadata.UploadTypeEnum.SOFTWARE);
        // A metadata description is required for Zenodo
        String description = workflow.getDescription();
        // The Zenodo API requires at description of at least three characters
        String descriptionStr = (description == null || description.isEmpty()) ? "no description" : workflow.getDescription();
        depositMetadata.setDescription(descriptionStr);

        // We will set the Zenodo workflow version publication date to the date of the DOI issuance
        depositMetadata.setPublicationDate(ZonedDateTime.now().toLocalDate().toString());

        depositMetadata.setVersion(workflowVersion.getName());

        setMetadataKeywords(depositMetadata, workflow);

        setMetadataRelatedIdentifiers(depositMetadata, workflow);

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
            OutputStream outputStream;
            // Replace forward slashes so we can use the version in a file name
            String versionOfWorkflow = workflowVersion.getName().replaceAll("/", "-");
            // Replace forward slashes so we can use the workflow path in a file name
            String fileNameBase = workflow.getWorkflowPath().replaceAll("/", "-")
                    + "_" + versionOfWorkflow;
            String fileSuffix = ".zip";
            String fileName = fileNameBase + fileSuffix;
            java.nio.file.Path tempDirPath;
            try {
                tempDirPath = Files.createTempDirectory(null);
            } catch (IOException e) {
                LOG.error("Could not create Zenodo temp upload directory." + " Error is " + e.getMessage(), e);
                throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            String zipFilePathName = tempDirPath.toString() + "/" + fileName;

            try {
                outputStream = new FileOutputStream(zipFilePathName);
            } catch (FileNotFoundException fne) {
                // Delete the temporary directory
                FileUtils.deleteQuietly(tempDirPath.toFile());
                LOG.error("Could not create file " + zipFilePathName
                        + " outputstream for DOI zip file for upload to Zenodo."
                        + " Error is " + fne.getMessage(), fne);
                throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            entryVersionHelper.writeStreamAsZip(sourceFiles, outputStream, Paths.get(zipFilePathName));
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
     */
    private static void putDepositionOnZenodo(DepositsApi depositApi, DepositMetadata depositMetadata,
            int depositionID) {
        NestedDepositMetadata nestedDepositMetadata = new NestedDepositMetadata();
        nestedDepositMetadata.setMetadata(depositMetadata);
        try {
            depositApi.putDeposit(depositionID, nestedDepositMetadata);
        } catch (ApiException e) {
            LOG.error("Could not put deposition metadata on Zenodo. Error is " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not put deposition metadata on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Publish the deposit on Zenodo
     * @param actionsApi Zenodo API for publishing deposits
     * @param depositionID Zenodo's ID for the deposition
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

}
