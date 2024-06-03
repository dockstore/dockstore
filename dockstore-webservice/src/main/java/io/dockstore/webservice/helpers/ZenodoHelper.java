package io.dockstore.webservice.helpers;

import static io.swagger.api.impl.ToolsImplCommon.WORKFLOW_PREFIX;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.Doi.DoiType;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.OrcidAuthorInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version.ReferenceType;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.DoiDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dockstore.webservice.resources.AliasableResourceInterface;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import io.dockstore.webservice.resources.SourceControlResourceInterface;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.zenodo.client.ApiClient;
import io.swagger.zenodo.client.ApiException;
import io.swagger.zenodo.client.api.AccessLinksApi;
import io.swagger.zenodo.client.api.ActionsApi;
import io.swagger.zenodo.client.api.DepositsApi;
import io.swagger.zenodo.client.api.FilesApi;
import io.swagger.zenodo.client.model.AccessLink;
import io.swagger.zenodo.client.model.Author;
import io.swagger.zenodo.client.model.Community;
import io.swagger.zenodo.client.model.Deposit;
import io.swagger.zenodo.client.model.DepositMetadata;
import io.swagger.zenodo.client.model.LinkPermissionSettings;
import io.swagger.zenodo.client.model.LinkPermissionSettings.PermissionEnum;
import io.swagger.zenodo.client.model.NestedDepositMetadata;
import io.swagger.zenodo.client.model.RelatedIdentifier;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZenodoHelper {
    // The max number of versions to process when automatically creating DOIs for a workflow
    public static final int AUTOMATIC_DOI_CREATION_VERSIONS_LIMIT = 10;
    public static final String NO_ZENODO_USER_TOKEN = "Could not get Zenodo token for user";
    public static final String AT_LEAST_ONE_AUTHOR_IS_REQUIRED_TO_PUBLISH_TO_ZENODO = "At least one author is required to publish to Zenodo";
    public static final String FROZEN_VERSION_REQUIRED = "Frozen version required to generate DOI";
    public static final String UNHIDDEN_VERSION_REQUIRED = "Unhidden version required to generate DOI";
    public static final String PUBLISHED_ENTRY_REQUIRED = "Published entry required to generate DOI";
    public static final String VERSION_ALREADY_HAS_DOI = "Version already has DOI. Dockstore can only create one DOI per version.";
    public static final String NO_DOCKSTORE_DOI = "The entry does not have DOIs created by Dockstore's Zenodo account.";
    public static final String ACCESS_LINK_DOESNT_EXIST = "The entry does not have an access link";
    public static final String ACCESS_LINK_ALREADY_EXISTS = "The entry already has an access link";
    private static final Logger LOG = LoggerFactory.getLogger(ZenodoHelper.class);
    private static String dockstoreUrl; // URL for Dockstore (e.g. https://dockstore.org)
    private static String dockstoreGA4GHBaseUrl; // The baseURL for GA4GH tools endpoint (e.g. "http://localhost:8080/api/api/ga4gh/v2/tools/")
    private static String dockstoreZenodoAccessToken;
    private static String dockstoreZenodoCommunityId;
    private static HttpClient httpClient;
    private static SessionFactory sessionFactory;
    private static DoiDAO doiDAO;
    private static TokenDAO tokenDAO;
    private static WorkflowDAO workflowDAO;
    private static WorkflowVersionDAO workflowVersionDAO;

    private static String zenodoUrl;
    private static String zenodoClientID;
    private static String zenodoClientSecret;

    private ZenodoHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration configuration, HttpClient initHttpClient, SessionFactory initSessionFactory) {
        initConfig(configuration);
        httpClient = initHttpClient;
        sessionFactory = initSessionFactory;
        doiDAO = new DoiDAO(sessionFactory);
        tokenDAO = new TokenDAO(sessionFactory);
        workflowDAO = new WorkflowDAO(sessionFactory);
        workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
    }

    static void initConfig(DockstoreWebserviceConfiguration configuration) {
        dockstoreUrl = configuration.getExternalConfig().computeBaseUrl();
        dockstoreZenodoAccessToken = configuration.getDockstoreZenodoAccessToken();
        dockstoreZenodoCommunityId = configuration.getDockstoreZenodoCommunityId();
        zenodoUrl = configuration.getZenodoUrl();
        zenodoClientID = configuration.getZenodoClientID();
        zenodoClientSecret = configuration.getZenodoClientSecret();
        try {
            dockstoreGA4GHBaseUrl = ToolsImplCommon.baseURL(configuration);
        } catch (URISyntaxException e) {
            LOG.error("Could create Dockstore base URL. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could create Dockstore base URL. "
                    + "Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Automatically registers the Zenodo DOI using the Dockstore Zenodo user.
     * @param workflow
     * @param workflowVersion
     * @param workflowOwner
     * @param authenticatedResourceInterface
     * @return
     */
    public static void automaticallyRegisterDockstoreDOI(Workflow workflow, WorkflowVersion workflowVersion, User workflowOwner, AuthenticatedResourceInterface authenticatedResourceInterface) {
        if (StringUtils.isEmpty(dockstoreZenodoAccessToken)) {
            LOG.error("Dockstore Zenodo access token not found for automatic DOI creation, skipping");
            return;
        }

        if (canAutomaticallyCreateDockstoreOwnedDoi(workflow, workflowVersion)) {
            ApiClient zenodoClient = createDockstoreZenodoClient();
            try {
                // Perform some checks to increase the chance of a DOI being successfully created
                checkCanRegisterDoi(workflow, workflowVersion, workflowOwner, DoiInitiator.DOCKSTORE);
                LOG.info("Automatically registering Dockstore owned Zenodo DOI for {}", workflowNameAndVersion(workflow, workflowVersion));
                registerZenodoDOI(zenodoClient, workflow, workflowVersion, workflowOwner, authenticatedResourceInterface,
                        DoiInitiator.DOCKSTORE);
            } catch (CustomWebApplicationException e) {
                LOG.error("Could not automatically register DOI for {}", workflowNameAndVersion(workflow, workflowVersion), e);
            }
        }
    }

    /**
     * Attempts to automatically register Dockstore DOIs for the most recent tags of a workflow.
     * @param workflow
     * @param workflowOwner
     * @param authenticatedResourceInterface
     */
    public static void automaticallyRegisterDockstoreDOIForRecentTags(Workflow workflow, User workflowOwner, AuthenticatedResourceInterface authenticatedResourceInterface) {
        final List<WorkflowVersion> recentTags = workflowVersionDAO.getTagsByWorkflowIdOrderedByLastModified(workflow.getId(), AUTOMATIC_DOI_CREATION_VERSIONS_LIMIT);
        for (WorkflowVersion tag: recentTags) {
            automaticallyRegisterDockstoreDOI(workflow, tag, workflowOwner, authenticatedResourceInterface);
        }
    }

    /**
     * Create a Zenodo client with the access token
     * @param zenodoAccessToken
     * @return
     */
    public static ApiClient createZenodoClient(String zenodoAccessToken) {
        ApiClient zenodoClient = new ApiClient();
        // for testing, either 'https://sandbox.zenodo.org/api' or 'https://zenodo.org/api' is the first parameter
        String zenodoUrlApi = zenodoUrl + "/api";
        zenodoClient.setBasePath(zenodoUrlApi);
        zenodoClient.setApiKey(zenodoAccessToken);
        return zenodoClient;
    }

    /**
     * Creates a Zenodo ApiClient using the user's Zenodo token if it exists, throws an exception otherwise.
     * @param user
     * @return
     */
    public static ApiClient createUserZenodoClient(User user) {
        Optional<Token> zenodoToken = getZenodoToken(user);

        // Update the zenodo token in case it changed. This handles the case where the token has been changed but an error occurred, so the token in the database was not updated
        if (zenodoToken.isPresent()) {
            tokenDAO.update(zenodoToken.get());
            sessionFactory.getCurrentSession().getTransaction().commit();
            sessionFactory.getCurrentSession().beginTransaction();
        } else {
            LOG.error("{} {}", NO_ZENODO_USER_TOKEN, user.getUsername());
            throw new CustomWebApplicationException(NO_ZENODO_USER_TOKEN + " " + user.getUsername(), HttpStatus.SC_BAD_REQUEST);
        }

        final String zenodoAccessToken = zenodoToken.get().getContent();
        return createZenodoClient(zenodoAccessToken);
    }

    /**
     * Creates a Zenodo client using Dockstore's Zenodo access token. Meant to be used with automatic DOI creation.
     * @return
     */
    public static ApiClient createDockstoreZenodoClient() {
        return createZenodoClient(dockstoreZenodoAccessToken);
    }

    /**
     * Get the Zenodo access token and refresh it if necessary
     *
     * @param user Dockstore with Zenodo account
     */
    public static List<Token> checkOnZenodoToken(User user) {
        List<Token> tokens = tokenDAO.findZenodoByUserId(user.getId());
        if (!tokens.isEmpty()) {
            Token zenodoToken = tokens.get(0);

            // Check that token is an hour old
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime updateTime = zenodoToken.getDbUpdateDate().toLocalDateTime();
            if (now.isAfter(updateTime.plusHours(1).minusMinutes(1))) {
                LOG.info("Refreshing the Zenodo Token");
                String refreshUrl = zenodoUrl + "/oauth/token";
                String payload = "client_id=" + zenodoClientID + "&client_secret=" + zenodoClientSecret
                        + "&grant_type=refresh_token&refresh_token=" + zenodoToken.getRefreshToken();
                SourceControlResourceInterface.refreshToken(refreshUrl, zenodoToken, httpClient, tokenDAO, payload);
            }
        }
        return tokenDAO.findZenodoByUserId(user.getId());
    }

    /**
     * Get the Zenodo token for the user.
     * @param user
     * @return
     */
    public static Optional<Token> getZenodoToken(User user) {
        List<Token> tokens = checkOnZenodoToken(user);
        Token zenodoToken = Token.extractToken(tokens, TokenType.ZENODO_ORG);
        return Optional.ofNullable(zenodoToken);
    }

    /**
     * Register a Zenodo DOI for the workflow version
     * @param zenodoClient Client for interacting with Zenodo server
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     */
    public static ZenodoDoiResult registerZenodoDOI(ApiClient zenodoClient, Workflow workflow,
            WorkflowVersion workflowVersion, User workflowOwner, AuthenticatedResourceInterface authenticatedResourceInterface, DoiInitiator doiInitiator) {

        LOG.info("Registering {} Zenodo DOI for workflow {}, version {}", doiInitiator.name(), workflow.getWorkflowPath(), workflowVersion.getName());
        // Create Dockstore workflow URL (e.g. https://dockstore.org/workflows/github.com/DataBiosphere/topmed-workflows/UM_variant_caller_wdl)
        String workflowUrl = MetadataResourceHelper.createWorkflowURL(workflow);

        DepositsApi depositApi = new DepositsApi(zenodoClient);
        ActionsApi actionsApi = new ActionsApi(zenodoClient);
        Deposit deposit = new Deposit();
        Deposit returnDeposit;
        checkForExistingDOIForWorkflowVersion(workflowVersion, doiInitiator);
        Optional<String> existingWorkflowVersionDOIURL = getAnExistingDOIForWorkflow(workflow, doiInitiator);

        int depositionID;
        DepositMetadata depositMetadata;
        String doiAlias;
        if (existingWorkflowVersionDOIURL.isEmpty()) {
            try {
                // No DOI has been assigned to any version of the workflow yet
                // So create a new deposit which will enable creation of a new
                // concept DOI and new version DOI.
                // The returned deposit will contain
                // the reserved DOI which we can use to create a workflow alias
                // Later on we will update the Zenodo deposit (put the deposit on
                // Zenodo again  in the call to putDepositionOnZenodo) so it contains the workflow version alias
                // constructed with the DOI
                returnDeposit = depositApi.createDeposit(deposit);
                depositionID = returnDeposit.getId();
                depositMetadata = returnDeposit.getMetadata();
            } catch (ApiException e) {
                LOG.error("Could not create deposition on Zenodo. Error is {}", e.getMessage(), e);
                throw new CustomWebApplicationException("Could not create deposition on Zenodo. "
                        + "Error is " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            String depositIdStr = extractRecordIdFromDoi(existingWorkflowVersionDOIURL.get());
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
            } catch (ApiException e) {
                LOG.error("Could not create new deposition version on Zenodo. Error is {}", e.getMessage(), e);
                if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
                    // Another user in the same organization already requested a DOI for a workflow version and created the workflow concept DOI.
                    // We are unable to create a new deposition version using the current user's Zenodo credentials because they don't have permission to create a deposition version for the original concept DOI.
                    // The workaround is for the user who created the concept DOI to request DOI's for other versions whenever it's needed by users from the same organization.
                    // This will hopefully be revisited later when Zenodo implements a feature where a deposit can be shared among users.
                    final String conceptDoi = workflow.getConceptDois().get(doiInitiator).getName();
                    String errorMessage = String.format(
                            "Could not create new deposition version on Zenodo because you do not have permission to create a deposition version for DOI %s. "
                                    + "Please ask the person who created DOI %s to request a DOI for workflow version %s on Dockstore.",
                            conceptDoi, conceptDoi, workflowVersion.getName());
                    throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
                } else {
                    throw new CustomWebApplicationException("Could not create new deposition version on Zenodo."
                        + " Error is " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
                }
            }
        }

        // Retrieve the DOI so we can use it to create a Dockstore alias
        // to the workflow; we will add that alias as a Zenodo related identifier
        String doi = depositMetadata.getPrereserveDoi().getDoi();
        doiAlias = createAliasUsingDoi(doi);
        setMetadataRelatedIdentifiers(depositMetadata, workflowUrl, workflow, workflowVersion, doiAlias);
        fillInMetadata(depositMetadata, workflow, workflowVersion);

        provisionWorkflowVersionUploadFiles(zenodoClient, returnDeposit, depositionID,
                workflow, workflowVersion);

        putDepositionOnZenodo(depositApi, depositMetadata, depositionID);

        Deposit publishedDeposit = publishDepositOnZenodo(actionsApi, depositionID);

        String conceptDoi = publishedDeposit.getConceptdoi();

        ZenodoDoiResult zenodoDoiResult = new ZenodoDoiResult(doiAlias, publishedDeposit.getMetadata().getDoi(), conceptDoi);
        workflowVersion.getDois().put(doiInitiator, getDoiFromDatabase(DoiType.VERSION, doiInitiator, zenodoDoiResult.doiUrl()));
        workflow.getConceptDois().put(doiInitiator, getDoiFromDatabase(DoiType.CONCEPT, doiInitiator, zenodoDoiResult.conceptDoi()));

        // Only add the alias to the workflow version after publishing the DOI succeeds
        // Otherwise if the publish call fails we will have added an alias
        // that will not be used and cannot be deleted
        // This code also checks that the alias does not start with an invalid prefix
        // If it does, this will generate an exception, the alias will not be added
        // to the workflow version, but there may be an invalid Related Identifier URL on the Zenodo entry
        AliasHelper.addWorkflowVersionAliasesAndCheck(authenticatedResourceInterface, workflowDAO, workflowVersionDAO, workflowOwner,
                workflowVersion.getId(), zenodoDoiResult.doiAlias(), false);

        return zenodoDoiResult;
    }

    public static Doi getDoiFromDatabase(DoiType doiType, DoiInitiator doiInitiator, String doiName) {
        Doi doi = doiDAO.findByName(doiName);
        if (doi == null) {
            long doiId = doiDAO.create(new Doi(doiType, doiInitiator, doiName));
            return doiDAO.findById(doiId);
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
     * @return TRS URL to workflow (e.g. https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere
     *     %2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl)
     */
    protected static String createWorkflowTrsUrl(Workflow workflow, WorkflowVersion workflowVersion) {
        final String sourceControlPath = workflow.getWorkflowPath();
        final String workflowVersionPrimaryDescriptorPath = WORKFLOW_PREFIX + "/" + sourceControlPath;
        final String workflowVersionPrimaryDescriptorPathPlainText;
        try {
            workflowVersionPrimaryDescriptorPathPlainText =
                    ToolsImplCommon.getUrl(workflowVersionPrimaryDescriptorPath, dockstoreGA4GHBaseUrl);
        } catch (UnsupportedEncodingException e) {
            LOG.error("Could not create Zenodo related identifier. Error is {}", e.getMessage(), e);
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
     * @param workflowUrl Dockstore workflow URL (e.g. https://dockstore.org/workflows/github.com/DataBiosphere/topmed-workflows/UM_variant_caller_wdl)
     * @param workflow workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     * @param doiAlias workflow alias constructed using a DOI
     */
    private static void setMetadataRelatedIdentifiers(DepositMetadata depositMetadata,
            String workflowUrl, Workflow workflow, WorkflowVersion workflowVersion, String doiAlias) {

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
        final String workflowVersionTrsUrl = createWorkflowTrsUrl(workflow, workflowVersion);
        addUriToRelatedIdentifierList(relatedIdentifierList, workflowVersionTrsUrl);

        depositMetadata.setRelatedIdentifiers(relatedIdentifierList);
    }

    /**
     * Add the workflow authors as creators to the deposition metadata
     * @param depositMetadata Metadata for the workflow version
     * @param workflow    workflow for which DOI is registered
     */
    static void setMetadataCreator(DepositMetadata depositMetadata, Workflow workflow, WorkflowVersion workflowVersion) {
        final Set<Author> setOfAuthors = getAndCheckAuthorsForMetadataCreator(workflow, workflowVersion);
        depositMetadata.setCreators(setOfAuthors.stream().toList());
    }

    private static Set<Author> getAndCheckAuthorsForMetadataCreator(Workflow workflow, WorkflowVersion workflowVersion) {
        // prefer authors from the specific workflow version
        final Set<Author> setOfAuthors = new HashSet<>(getAuthors(workflowVersion.getAuthors(), workflowVersion.getOrcidAuthors()));
        /// but use the default if necessary
        if (setOfAuthors.isEmpty()) {
            final List<Author> zenodoAuthors = getAuthors(workflow.getAuthors(), workflow.getOrcidAuthors());
            setOfAuthors.addAll(zenodoAuthors);
        }

        if (setOfAuthors.isEmpty()) {
            throw new CustomWebApplicationException(AT_LEAST_ONE_AUTHOR_IS_REQUIRED_TO_PUBLISH_TO_ZENODO, HttpStatus.SC_BAD_REQUEST);
        }

        return setOfAuthors;
    }

    private static List<Author> getAuthors(Set<io.dockstore.webservice.core.Author> inputAuthors, Set<OrcidAuthor> inputOrcidAuthors) {
        final Stream<Author> authors = inputAuthors.stream().map(ZenodoHelper::fromDockstoreAuthor);
        final Stream<Author> orcidAuthors = inputOrcidAuthors.stream()
                .map(OrcidAuthor::getOrcid)
                .map(orcidId -> ORCIDHelper.getOrcidAuthorInformation(orcidId, null))
                .flatMap(Optional::stream)
                .map(ZenodoHelper::fromOrcidAuthorInfo);
        return Stream.concat(authors, orcidAuthors).toList();
    }

    private static Author fromDockstoreAuthor(io.dockstore.webservice.core.Author dockstoreAuthor) {
        final Author author = new Author();
        author.setName(dockstoreAuthor.getName());
        author.setAffiliation(dockstoreAuthor.getAffiliation());
        return author;
    }

    private static Author fromOrcidAuthorInfo(OrcidAuthorInformation orcidAuthorInformation) {
        final Author author = ZenodoHelper.fromDockstoreAuthor(orcidAuthorInformation);
        author.setOrcid(orcidAuthorInformation.getOrcid());
        return author;
    }

    /**
     * Add a communities list to the deposition metadata even if it is empty
     * @param depositMetadata Metadata for the workflow version
     */
    static void setMetadataCommunities(DepositMetadata depositMetadata) {
        // A communities entry must not be null, but it can be a null
        // List for Zenodo
        List<Community> communities = depositMetadata.getCommunities();
        List<Community> myList = new ArrayList<>();
        if (StringUtils.isNotEmpty(dockstoreZenodoCommunityId)) {
            // Adding the record to the Dockstore community gives Dockstore the ability to create new versions if the user created the first DOI and they later unlink their account.
            Community dockstoreCommunity = new Community();
            dockstoreCommunity.setIdentifier(dockstoreZenodoCommunityId);
            myList.add(dockstoreCommunity);
        }
        if (communities == null || communities.isEmpty()) {
            depositMetadata.setCommunities(myList);
        } else if (communities.size() == 1 && communities.get(0).getIdentifier() == null) {
            // Sometimes the list of communities contains one object
            // with a null id when Zenodo copies the metadata.
            // This will cause the call to publish to fail, so clear
            // the list of communities in this case
            depositMetadata.setCommunities(myList);
        } else {
            myList.addAll(communities);
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

        setMetadataCreator(depositMetadata, workflow, workflowVersion);

        setMetadataCommunities(depositMetadata);
    }

    /**
     * Provision files to upload to Zenodo
     * @param zendoClient Zenodo api client
     * @param returnDeposit Deposit object for the new version
     * @param depositionID ID of Zendo deposit to which files will be attached
     * @param workflow    workflow for which DOI is registered
     * @param workflowVersion workflow version for which DOI is registered
     */
    private static void provisionWorkflowVersionUploadFiles(ApiClient zendoClient, Deposit returnDeposit,
            int depositionID, Workflow workflow, WorkflowVersion workflowVersion) {
        // Creating a new version copies the files from the previous version
        // We want to delete these since we will upload a new set of files
        // if creating a completely new deposit this should not cause a problem
        FilesApi filesApi = new FilesApi(zendoClient);

        returnDeposit.getFiles().forEach(file -> {
            String fileIdStr = file.getId();
            filesApi.deleteFile(depositionID, fileIdStr);
        });

        // Add workflow version source files as a zip to the DOI upload deposit
        checkHasSourceFiles(workflowVersion);

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
            LOG.error("Could not create Zenodo temp upload directory. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        String zipFilePathName = tempDirPath.toString() + "/" + fileName;

        try (OutputStream outputStream = new FileOutputStream(zipFilePathName)) {
            EntryVersionHelper.writeStreamAsZipStatic(workflowVersion.getSourceFiles(), outputStream, Paths.get(zipFilePathName));
        } catch (IOException fne) {
            // Delete the temporary directory
            FileUtils.deleteQuietly(tempDirPath.toFile());
            LOG.error("Could not create file {} outputstream for DOI zip file for upload to Zenodo. Error is {}", zipFilePathName,
                    fne.getMessage(), fne);
            throw new CustomWebApplicationException("Internal server error creating Zenodo upload temp directory",
                HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        File zipFile = new File(zipFilePathName);

        try {
            filesApi.createFile(depositionID, zipFile, fileName);
        } catch (ApiException e) {
            LOG.error("Could not create files for new version on Zenodo. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could not create files for new version on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            // Delete the zip file in the temporary directory
            FileUtils.deleteQuietly(zipFile);
            // Delete the temporary directory
            FileUtils.deleteQuietly(tempDirPath.toFile());
        }
    }

    /**
     * Get an existing Zenodo DOI for the workflow if one exists
     * otherwise return null
     * @param workflow workflow
     * @return the DOI for a workflow
     */
    private static Optional<String> getAnExistingDOIForWorkflow(Workflow workflow, DoiInitiator doiInitiator) {
        // Find out if this workflow already has at least one
        // version that has been assigned a DOI
        // If a version DOI exists, we will create another version DOI
        // instead of creating a new workflow concept DOI and version DOI
        // Get the ID of one of the workflow version DOIs
        // because Zenodo requires that we use it to create the next
        // workflow version DOI

        return workflow.getWorkflowVersions().stream()
                .map(version -> version.getDois().get(doiInitiator))
                .filter(Objects::nonNull)
                .map(Doi::getName)
                .filter(doi -> !StringUtils.isEmpty(doi))
                .findAny();
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
            LOG.error("Could not publish DOI on Zenodo. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could not publish DOI on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return publishedDeposit;
    }

    /**
     * Creates an access link with edit permissions for the workflow's DOIs. This link can edit ALL versions of the DOI.
     * @param workflow
     */
    public static AccessLink createEditAccessLink(Workflow workflow) {
        String existingDockstoreVersionDoiName = checkAndGetDockstoreVersionDoiName(workflow);
        Doi dockstoreConceptDoi = workflow.getConceptDois().get(DoiInitiator.DOCKSTORE);
        checkAccessLinkDoesntExist(dockstoreConceptDoi);

        String recordId = extractRecordIdFromDoi(existingDockstoreVersionDoiName);
        AccessLinksApi accessLinksApi = new AccessLinksApi(createDockstoreZenodoClient());
        try {
            AccessLink accessLink = accessLinksApi.createAccessLink(recordId, new LinkPermissionSettings().permission(PermissionEnum.EDIT));
            // Save the access link to the concept DOI because this link can edit ALL versions
            dockstoreConceptDoi.setEditAccessLinkId(accessLink.getId());
            workflow.getConceptDois().put(DoiInitiator.DOCKSTORE, dockstoreConceptDoi);
            return accessLink;
        } catch (ApiException e) {
            LOG.error("Could not create edit access link on Zenodo. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could not create edit access link on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Gets an existing access link.
     * @param workflow
     */
    public static AccessLink getAccessLink(Workflow workflow) {
        String existingDockstoreVersionDoiName = checkAndGetDockstoreVersionDoiName(workflow);
        Doi dockstoreConceptDoi = workflow.getConceptDois().get(DoiInitiator.DOCKSTORE);
        checkAccessLinkExists(dockstoreConceptDoi);

        String recordId = extractRecordIdFromDoi(existingDockstoreVersionDoiName);
        AccessLinksApi accessLinksApi = new AccessLinksApi(createDockstoreZenodoClient());
        try {
            return accessLinksApi.getAccessLink(recordId, dockstoreConceptDoi.getEditAccessLinkId());
        } catch (ApiException e) {
            LOG.error("Could not get edit access link on Zenodo. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could not get edit access link on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes the access link with edit permissions for the workflow.
     * @param workflow
     */
    public static void deleteAccessLink(Workflow workflow) {
        String existingDockstoreVersionDoiName = checkAndGetDockstoreVersionDoiName(workflow);
        Doi dockstoreConceptDoi = workflow.getConceptDois().get(DoiInitiator.DOCKSTORE);
        checkAccessLinkExists(dockstoreConceptDoi);

        String recordId = extractRecordIdFromDoi(existingDockstoreVersionDoiName);
        AccessLinksApi accessLinksApi = new AccessLinksApi(createDockstoreZenodoClient());
        try {
            accessLinksApi.deleteAccessLink(recordId, dockstoreConceptDoi.getEditAccessLinkId());
            // The access link is stored by the concept DOI. Set the values to null
            dockstoreConceptDoi.setEditAccessLinkId(null);
        } catch (ApiException e) {
            LOG.error("Could not delete edit access link on Zenodo. Error is {}", e.getMessage(), e);
            throw new CustomWebApplicationException("Could not delete edit access link on Zenodo."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static String checkAndGetDockstoreVersionDoiName(Workflow workflow) {
        Optional<String> existingDockstoreVersionDoiURL = getAnExistingDOIForWorkflow(workflow, DoiInitiator.DOCKSTORE);
        if (existingDockstoreVersionDoiURL.isEmpty()) {
            LOG.error(NO_DOCKSTORE_DOI);
            throw new CustomWebApplicationException(NO_DOCKSTORE_DOI, HttpStatus.SC_BAD_REQUEST);
        }
        return existingDockstoreVersionDoiURL.get();
    }

    private static void checkAccessLinkExists(Doi conceptDoi) {
        if (conceptDoi.getEditAccessLinkId() == null) {
            LOG.error(ACCESS_LINK_DOESNT_EXIST);
            throw new CustomWebApplicationException(ACCESS_LINK_DOESNT_EXIST, HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static void checkAccessLinkDoesntExist(Doi conceptDoi) {
        if (conceptDoi.getEditAccessLinkId() != null) {
            LOG.error(ACCESS_LINK_ALREADY_EXISTS);
            throw new CustomWebApplicationException(ACCESS_LINK_ALREADY_EXISTS, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Gets the record ID from the DOI. Example: returns the record ID 372767 from the DOI 10.5072/zenodo.372767.
     * @param doi
     * @return
     */
    static String extractRecordIdFromDoi(String doi) {
        // A DOI is composed of a prefix and a suffix, separated by a slash. Ex: 10.5072/zenodo.372767
        return doi.substring(doi.lastIndexOf(".") + 1).trim();
    }

    /**
     * Performs various checks to ensure that a DOI can be registered.
     * @param workflow
     * @param workflowVersion
     * @param user
     */
    public static void checkCanRegisterDoi(Workflow workflow, WorkflowVersion workflowVersion, User user, DoiInitiator doiInitiator) {
        final String workflowNameAndVersion = workflowNameAndVersion(workflow, workflowVersion);

        if (!workflow.getIsPublished()) {
            LOG.error("{}: Could not generate DOI for {}. {}", user.getUsername(), workflowNameAndVersion, PUBLISHED_ENTRY_REQUIRED);
            throw new CustomWebApplicationException(PUBLISHED_ENTRY_REQUIRED, HttpStatus.SC_BAD_REQUEST);
        }

        // Only require snapshotting for user-created DOIs
        if (doiInitiator == DoiInitiator.USER && !workflowVersion.isFrozen()) {
            LOG.error("{}: Could not generate DOI for {}. {}", user.getUsername(), workflowNameAndVersion, FROZEN_VERSION_REQUIRED);
            throw new CustomWebApplicationException(String.format("Could not generate DOI for %s. %s", workflowNameAndVersion, FROZEN_VERSION_REQUIRED), HttpStatus.SC_BAD_REQUEST);
        }

        if (workflowVersion.isHidden()) {
            LOG.error("{}: Could not generate DOI for {}. {}", user.getUsername(), workflowNameAndVersion, UNHIDDEN_VERSION_REQUIRED);
            throw new CustomWebApplicationException(String.format("Could not generate DOI for %s. %s", workflowNameAndVersion, UNHIDDEN_VERSION_REQUIRED), HttpStatus.SC_BAD_REQUEST);
        }

        checkForExistingDOIForWorkflowVersion(workflowVersion, doiInitiator);
        checkHasSourceFiles(workflowVersion);
        getAndCheckAuthorsForMetadataCreator(workflow, workflowVersion);
    }

    private static String workflowNameAndVersion(Workflow workflow, WorkflowVersion workflowVersion) {
        return workflow.getWorkflowPath() + ":" + workflowVersion.getName();
    }

    /**
     * Check if a Zenodo DOI already exists for the workflow version
     * @param workflowVersion workflow version
     */
    private static void checkForExistingDOIForWorkflowVersion(WorkflowVersion workflowVersion, DoiInitiator doiInitiator) {
        if (hasExistingDOIForWorkflowVersion(workflowVersion, doiInitiator)) {
            LOG.error("Workflow version {} already has DOI {}. Dockstore can only create one DOI per version.", workflowVersion.getName(),
                    workflowVersion.getDois().get(doiInitiator).getName());
            throw new CustomWebApplicationException(VERSION_ALREADY_HAS_DOI, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private static boolean hasExistingDOIForWorkflowVersion(WorkflowVersion workflowVersion, DoiInitiator doiInitiator) {
        return workflowVersion.getDois().containsKey(doiInitiator);
    }

    private static void checkHasSourceFiles(WorkflowVersion workflowVersion) {
        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            LOG.warn("No source files found to zip when creating DOI");
            throw new CustomWebApplicationException(
                    "No source files found to upload when creating DOI. Zenodo requires at lease one file to be uploaded in order to create a DOI.",
                    HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Returns a boolean indicating if a DOI can automatically be created for the workflow version.
     * @param workflow
     * @param workflowVersion
     * @return
     */
    public static boolean canAutomaticallyCreateDockstoreOwnedDoi(Workflow workflow, WorkflowVersion workflowVersion) {
        final boolean validPublishedTag = workflow.getIsPublished() && workflowVersion.isValid() && workflowVersion.getReferenceType() == ReferenceType.TAG;
        return validPublishedTag && !hasExistingDOIForWorkflowVersion(workflowVersion, DoiInitiator.DOCKSTORE);
    }

    public record ZenodoDoiResult(String doiAlias, String doiUrl, String conceptDoi) {
    }

}
