/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.resources.proposedGA4GH;

import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static io.openapi.api.impl.ToolsApiServiceImpl.BAD_DECODE_REGISTRY_RESPONSE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dockstore.common.Partner;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.common.metrics.Execution;
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.common.metrics.MetricsDataS3Client;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.api.UpdateAITopicRequest;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.EntryLiteAndVersionName;
import io.dockstore.webservice.core.Entry.TopicSelection;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.database.EntryLite;
import io.dockstore.webservice.core.metrics.ExecutionResponse;
import io.dockstore.webservice.core.metrics.ExecutionsRequestBodyS3Handler;
import io.dockstore.webservice.core.metrics.ExecutionsRequestBodyS3Handler.ExecutionsFromS3;
import io.dockstore.webservice.core.metrics.ExecutionsResponseBody;
import io.dockstore.webservice.core.metrics.Metrics;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.openapi.api.impl.ToolsApiServiceImpl;
import io.swagger.api.impl.ToolsImplCommon;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Implementations of methods to return responses containing organization related information
 * @author kcao on 01/03/17.
 *
 */
public class ToolsApiExtendedServiceImpl extends ToolsExtendedApiService {

    public static final int ES_BATCH_INSERT_SIZE = 500;
    public static final String INVALID_PLATFORM = "Invalid platform. Please select an individual platform.";
    public static final String FORBIDDEN_PLATFORM = "You do not have the credentials to access executions for this platform";
    public static final String TOOL_NOT_FOUND_ERROR = "Tool not found";
    public static final String VERSION_NOT_FOUND_ERROR = "Version not found";
    public static final String SEARCH_QUERY_INVALID_JSON = "Search payload request is not valid JSON";
    public static final String SEARCH_QUERY_NOT_PARSED = "Couldn't parse search payload request.";
    public static final String SEARCH_QUERY_REGEX = "([.?+*#@&~\"{}()<>\\[\\]|\\\\])";
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiExtendedServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ToolsApiServiceImpl TOOLS_API_SERVICE_IMPL = new ToolsApiServiceImpl();

    private static final String TOOLS_INDEX = ElasticListener.TOOLS_INDEX;
    private static final String WORKFLOWS_INDEX = ElasticListener.WORKFLOWS_INDEX;
    private static final String NOTEBOOKS_INDEX = ElasticListener.NOTEBOOKS_INDEX;
    private static final String COMMA_SEPARATED_INDEXES = String.join(",", ElasticListener.INDEXES);
    private static final int SEARCH_TERM_LIMIT = 256;
    private static final int TOO_MANY_REQUESTS_429 = 429;
    private static final int ELASTICSEARCH_DEFAULT_LIMIT = 15;
    public static final String COULD_NOT_SUBMIT_METRICS_DATA = "Could not submit metrics data";
    public static final String COULD_NOT_UPDATE_EXECUTION = "Could not update execution";
    public static final String EXECUTION_NOT_FOUND_ERROR = "Execution not found";

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static AppToolDAO appToolDAO = null;
    private static NotebookDAO notebookDAO = null;
    private static BioWorkflowDAO bioWorkflowDAO = null;
    private static ServiceDAO serviceDAO = null;
    private static WorkflowVersionDAO workflowVersionDAO = null;
    private static DockstoreWebserviceConfiguration config = null;
    private static DockstoreWebserviceConfiguration.MetricsConfig metricsConfig = null;
    private static PublicStateManager publicStateManager = null;
    private static Semaphore elasticSearchConcurrencyLimit = null;

    public static void setStateManager(PublicStateManager manager) {
        ToolsApiExtendedServiceImpl.publicStateManager = manager;
    }

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiExtendedServiceImpl.toolDAO = toolDAO;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiExtendedServiceImpl.workflowDAO = workflowDAO;
    }

    public static void setAppToolDAO(AppToolDAO appToolDAO) {
        ToolsApiExtendedServiceImpl.appToolDAO = appToolDAO;
    }

    public static void setNotebookDAO(NotebookDAO notebookDAO) {
        ToolsApiExtendedServiceImpl.notebookDAO = notebookDAO;
    }

    public static void setBioWorkflowDAO(BioWorkflowDAO bioWorkflowDAO) {
        ToolsApiExtendedServiceImpl.bioWorkflowDAO = bioWorkflowDAO;
    }

    public static void setServiceDAO(ServiceDAO serviceDAO) {
        ToolsApiExtendedServiceImpl.serviceDAO = serviceDAO;
    }

    public static void setWorkflowVersionDAO(WorkflowVersionDAO workflowVersionDAO) {
        ToolsApiExtendedServiceImpl.workflowVersionDAO = workflowVersionDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiExtendedServiceImpl.config = config;
        ToolsApiExtendedServiceImpl.metricsConfig = config.getMetricsConfig();

        if (config.getEsConfiguration().getMaxConcurrentSessions() == null) {
            ToolsApiExtendedServiceImpl.elasticSearchConcurrencyLimit = new Semaphore(ELASTICSEARCH_DEFAULT_LIMIT);
        } else {
            ToolsApiExtendedServiceImpl.elasticSearchConcurrencyLimit = new Semaphore(config.getEsConfiguration().getMaxConcurrentSessions());
        }
    }

    /**
     * More optimized
     *
     * @param organization
     * @return
     */
    private List<Entry<?, ?>> getPublishedByOrganization(String organization) {
        final List<Entry<?, ?>> published = new ArrayList<>();
        published.addAll(workflowDAO.findPublishedByOrganization(organization));
        published.addAll(toolDAO.findPublishedByNamespace(organization));
        published.sort(Comparator.comparing(Entry::getGitUrl));
        return published;
    }

    @Override
    public Response toolsOrgGet(String organization, SecurityContext securityContext) {
        return Response.ok().entity(getPublishedByOrganization(organization)).build();
    }

    private List<io.openapi.model.Tool> workflowOrgGetList(String organization) {
        List<Workflow> published = workflowDAO.findPublishedByOrganization(organization);
        return published.stream().map(c -> ToolsImplCommon.convertEntryToTool(c, config)).collect(Collectors.toList());
    }

    private List<io.openapi.model.Tool> entriesOrgGetList(String organization) {
        List<Tool> published = toolDAO.findPublishedByNamespace(organization);
        return published.stream().map(c -> ToolsImplCommon.convertEntryToTool(c, config)).collect(Collectors.toList());
    }

    @Override
    public Response workflowsOrgGet(String organization, SecurityContext securityContext) {
        return Response.ok(workflowOrgGetList(organization)).build();
    }

    @Override
    public Response entriesOrgGet(String organization, SecurityContext securityContext) {
        return Response.ok(entriesOrgGetList(organization)).build();
    }

    @Override
    public Response organizationsGet(SecurityContext securityContext) {
        Set<String> organizations = new HashSet<>();
        organizations.addAll(toolDAO.getAllPublishedNamespaces());
        organizations.addAll(workflowDAO.getAllPublishedOrganizations());
        return Response.ok(new ArrayList<>(organizations)).build();
    }

    @Override
    public Response toolsIndexGet(SecurityContext securityContext) {
        int totalProcessed = 0;
        if (!config.getEsConfiguration().getHostname().isEmpty()) {
            clearElasticSearch();
            LOG.info("Starting GA4GH batch processing");
            totalProcessed += indexDAO(toolDAO);
            LOG.info("Processed {} tools", totalProcessed);
            totalProcessed += indexDAO(workflowDAO);
            LOG.info("Processed {} tools and workflows", totalProcessed);
            totalProcessed += indexDAO(appToolDAO);
            LOG.info("Processed {} tools, workflows, and apptools", totalProcessed);
            totalProcessed += indexDAO(notebookDAO);
            LOG.info("Processed {} tools, workflows, apptools, and notebooks", totalProcessed);
        }
        return Response.ok().entity(totalProcessed).build();
    }

    private int indexDAO(EntryDAO entryDAO) {
        int processed = 0;
        List<? extends Entry> published;
        do {
            published = entryDAO.findAllPublished(processed, ES_BATCH_INSERT_SIZE, null, "id", "asc");
            processed += published.size();
            indexBatch(published);
            published.forEach(entryDAO::evict);
        } while (published.size() == ES_BATCH_INSERT_SIZE);
        return processed;
    }

    private void clearElasticSearch() {
        try {
            // FYI. it is real tempting to use a try ... catch with resources to close this client, but it actually permanently messes up the client!
            RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
            // Delete previous indexes
            deleteIndex(TOOLS_INDEX, client);
            deleteIndex(WORKFLOWS_INDEX, client);
            deleteIndex(NOTEBOOKS_INDEX, client);
            // Create new indexes
            createIndex("queries/mapping_tool.json", TOOLS_INDEX, client);
            createIndex("queries/mapping_workflow.json", WORKFLOWS_INDEX, client);
            createIndex("queries/mapping_notebook.json", NOTEBOOKS_INDEX, client);
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not clear elastic search index", e);
            throw new CustomWebApplicationException("Search indexing failed", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void createIndex(String resourceName, String nameOfIndex, RestHighLevelClient client) throws IOException {
        // Get mapping for index
        URL urlStuff = Resources.getResource(resourceName);
        String textTools = Resources.toString(urlStuff, StandardCharsets.UTF_8);
        // Create indices
        CreateIndexRequest toolsRequest = new CreateIndexRequest(nameOfIndex);
        toolsRequest.source(textTools, XContentType.JSON);
        client.indices().create(toolsRequest, RequestOptions.DEFAULT);
    }

    private void indexBatch(List<? extends Entry> published) {
        // Populate index
        if (!published.isEmpty()) {
            publicStateManager.bulkUpsert((List<Entry>) published);
        }
    }

    @Override
    public Response toolsIndexSearch(String query, MultivaluedMap<String, String> queryParameters, SecurityContext securityContext) {
        String unableToUseESMsg = "Could not use Elasticsearch search";
        if (!elasticSearchConcurrencyLimit.tryAcquire(1)) {
            LOG.error(unableToUseESMsg + ": too many concurrent Elasticsearch requests.");
            throw new CustomWebApplicationException(unableToUseESMsg, TOO_MANY_REQUESTS_429);
        }
        try {
            if (!config.getEsConfiguration().getHostname().isEmpty()) {
                String searchQuery = escapeCharactersInSearchTerm(query);
                checkSearchTermLimit(searchQuery);
                try {
                    RestClient restClient = ElasticSearchHelper.restClient();
                    Map<String, String> parameters = new HashMap<>();
                    // TODO: note that this is lossy if there are repeated parameters
                    // but it looks like the elastic search http client classes don't handle it
                    if (queryParameters != null) {
                        queryParameters.forEach((key, value) -> parameters.put(key, value.get(0)));
                    }
                    // This should be using the high-level Elasticsearch client instead
                    Request request = new Request("GET", "/" + COMMA_SEPARATED_INDEXES + "/_search");
                    if (searchQuery != null) {
                        request.setJsonEntity(searchQuery);
                    }
                    request.addParameters(parameters);
                    org.elasticsearch.client.Response get = restClient.performRequest(request);
                    if (get.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        throw new CustomWebApplicationException("Could not search " + COMMA_SEPARATED_INDEXES + " index",
                            HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                    return Response.ok().entity(get.getEntity().getContent()).build();
                } catch (ResponseException e) {
                    // Only surface these codes to the user, everything else is not entirely obvious so returning 500 instead.
                    int[] codesToResurface = {HttpStatus.SC_BAD_REQUEST};
                    int statusCode = e.getResponse().getStatusLine().getStatusCode();
                    LOG.error(unableToUseESMsg, e);
                    // Provide a minimal amount of error information in the browser console as outlined by
                    // https://ucsc-cgl.atlassian.net/browse/SEAB-2128
                    String reasonPhrase = e.getResponse().getStatusLine().getReasonPhrase();
                    if (ArrayUtils.contains(codesToResurface, statusCode)) {
                        throw new CustomWebApplicationException(reasonPhrase, statusCode);
                    } else {
                        throw new CustomWebApplicationException(reasonPhrase, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                } catch (IOException e2) {
                    LOG.error(unableToUseESMsg, e2);
                    throw new CustomWebApplicationException("Search failed", HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
            }
            return Response.ok().entity(0).build();
        } finally {
            elasticSearchConcurrencyLimit.release(1);
        }
    }

    /**
     * Certain characters need to be escaped in the search term ("include" key in request payload) or the request will fail
     * See reserved characters here: https://www.elastic.co/guide/en/elasticsearch/reference/current/regexp-syntax.html#regexp-optional-operators
     * @param query
     * @return a query with the modified search term
     */
    protected static String escapeCharactersInSearchTerm(String query) {
        if (query != null) {
            JSONObject json;
            try {
                json = new JSONObject(query);
            } catch (JSONException ex) {
                LOG.error(SEARCH_QUERY_INVALID_JSON, ex);
                throw new CustomWebApplicationException(SEARCH_QUERY_INVALID_JSON, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
            }

            try {
                String include = getSearchQueryJsonIncludeKey(json);
                if (include.length() > 0) {
                    // A trailing .* is added by the ui when creating the request body with an autocomplete field
                    // It gets removed here and added back later so the search recognizes it as a regex expression rather than literal characters
                    String escapedStr = include.replaceAll("\\.\\*$", "").replaceAll(SEARCH_QUERY_REGEX, "\\\\$1");
                    if (include.endsWith(".*")) {
                        escapedStr = escapedStr + ".*";
                    }
                    json.getJSONObject("aggs").getJSONObject("autocomplete").getJSONObject("terms").put("include", escapedStr);
                }
            } catch (JSONException ex) { // The request bodies all look pretty different, so it's okay for the exception to get thrown.
                LOG.debug(SEARCH_QUERY_NOT_PARSED);
            }

            return json.toString();
        }
        return query;
    }

    /**
     * Performing a search on the UI sends multiple POST requests. When the search term ("value" key of a wildcard or "include" key in request payload) is large,
     * the POST requests containing these keys will fail.
     * @param query
     */
    protected static void checkSearchTermLimit(String query) {
        if (query != null) {
            JSONObject json;
            try {
                json = new JSONObject(query);
            } catch (JSONException ex) {
                LOG.error(SEARCH_QUERY_INVALID_JSON, ex);
                throw new CustomWebApplicationException(SEARCH_QUERY_INVALID_JSON, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
            }

            try {
                String include = getSearchQueryJsonIncludeKey(json);
                if (include.length() > SEARCH_TERM_LIMIT) {
                    throw new CustomWebApplicationException("Search request exceeds limit", HttpStatus.SC_REQUEST_TOO_LONG);
                }
            } catch (JSONException ex) { // The request bodies all look pretty different, so it's okay for the exception to get thrown.
                LOG.debug(SEARCH_QUERY_NOT_PARSED);
            }

            try {
                JSONArray should = json.getJSONObject("query").getJSONObject("bool").getJSONObject("filter").getJSONObject("bool").getJSONArray("should");
                for (int i = 0; i < should.length(); i++) {
                    JSONObject wildcard = should.getJSONObject(i).optJSONObject("wildcard");

                    if (wildcard != null) {
                        JSONObject pathKeyword = null;
                        if (wildcard.has("full_workflow_path.keyword")) {
                            pathKeyword = wildcard.getJSONObject("full_workflow_path.keyword");
                        } else if (wildcard.has("tool_path.keyword")) {
                            pathKeyword = wildcard.getJSONObject("tool_path.keyword");
                        }

                        if (pathKeyword != null) {
                            String value = pathKeyword.optString("value");
                            if (value.length() > SEARCH_TERM_LIMIT) {
                                throw new CustomWebApplicationException("Search request exceeds limit", HttpStatus.SC_REQUEST_TOO_LONG);
                            }
                        }
                    }
                }
            } catch (JSONException ex) { // The request bodies all look pretty different, so it's okay for the exception to get thrown.
                LOG.debug(SEARCH_QUERY_NOT_PARSED);
            }
        }
    }

    /**
     * @param json
     * @return "include" key string
     */
    protected static String getSearchQueryJsonIncludeKey(JSONObject json) {
        try {
            return json.getJSONObject("aggs").getJSONObject("autocomplete").getJSONObject("terms").getString("include");
        } catch (JSONException ex) {
            LOG.debug(SEARCH_QUERY_NOT_PARSED);
        }
        return "";
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    public Response setSourceFileMetadata(String type, String id, String versionId, String platform, String platformVersion, String relativePath, Boolean verified,
        String metadata) {

        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        Optional<? extends Version<?>> versionOptional;

        if (entry instanceof Workflow || entry instanceof Tool) {
            versionOptional = getVersion(entry, versionId);
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (versionOptional.isPresent()) {
            Version<?> version = versionOptional.get();
            // so in this stream we need to standardize relative to the main descriptor
            Optional<SourceFile> correctSourceFile = TOOLS_API_SERVICE_IMPL
                .lookForFilePath(version.getSourceFiles(), relativePath, version.getWorkingDirectory());
            if (correctSourceFile.isPresent()) {
                SourceFile sourceFile = correctSourceFile.get();
                if (!(SourceFile.TEST_FILE_TYPES.contains(sourceFile.getType()))) {
                    throw new CustomWebApplicationException("File was not a test parameter file", HttpStatus.SC_BAD_REQUEST);
                }
                if (verified == null) {
                    sourceFile.getVerifiedBySource().remove(platform);
                } else {
                    SourceFile.VerificationInformation verificationInformation = new SourceFile.VerificationInformation();
                    verificationInformation.metadata = metadata;
                    verificationInformation.verified = verified;
                    verificationInformation.platformVersion = platformVersion;
                    sourceFile.getVerifiedBySource().put(platform, verificationInformation);
                }
                // denormalizes verification out to the version level for performance
                // not sure why the cast is needed
                version.updateVerified();
                return Response.ok().entity(sourceFile.getVerifiedBySource()).build();
            }
        }
        throw new CustomWebApplicationException("Could not submit verification information", HttpStatus.SC_BAD_REQUEST);
    }

    @Override
    public Response submitMetricsData(String id, String versionId, Partner platform, User owner, String description, ExecutionsRequestBody executions) {
        checkActualPlatform(platform);
        checkPlatformForRole(owner, platform);

        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.of(owner));
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        checkEntryNotNull(entry);

        Optional<? extends Version<?>> version = getVersion(entry, versionId);
        if (version.isEmpty()) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }

        MetricsDataS3Client metricsDataS3Client;
        try {
            metricsDataS3Client = new MetricsDataS3Client(metricsConfig.getS3BucketName(), metricsConfig.getS3EndpointOverride());
        } catch (URISyntaxException e) {
            throw new CustomWebApplicationException("Error creating S3 client, could not submit executions", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        try {
            String metricsData = OBJECT_MAPPER.writeValueAsString(executions);
            if (StringUtils.isBlank(metricsData)) {
                throw new CustomWebApplicationException("Execution metrics data must be provided", HttpStatus.SC_BAD_REQUEST);
            }

            metricsDataS3Client.createS3Object(id, versionId, platform.name(), S3ClientHelper.createFileName(), owner.getId(), description, metricsData);
            version.get().getVersionMetadata().setLatestMetricsSubmissionDate(Timestamp.from(Instant.now()));
            return Response.noContent().build();
        } catch (JsonProcessingException | AwsServiceException | SdkClientException e) {
            LOG.error(COULD_NOT_SUBMIT_METRICS_DATA, e);
            throw new CustomWebApplicationException(COULD_NOT_SUBMIT_METRICS_DATA, HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public Response getEntryVersionsToAggregate() {
        List<EntryLiteAndVersionName> entryAndVersionNames = new ArrayList<>();
        entryAndVersionNames.addAll(toolDAO.findEntryVersionsToAggregate());
        entryAndVersionNames.addAll(bioWorkflowDAO.findEntryVersionsToAggregate());
        entryAndVersionNames.addAll(notebookDAO.findEntryVersionsToAggregate());
        entryAndVersionNames.addAll(appToolDAO.findEntryVersionsToAggregate());
        entryAndVersionNames.addAll(serviceDAO.findEntryVersionsToAggregate());
        return Response.ok(entryAndVersionNames).build();
    }

    @Override
    public Response setAggregatedMetrics(String id, String versionId, Map<Partner, Metrics> aggregatedMetrics) {
        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        checkEntryNotNull(entry);

        Version<?> version = getVersion(entry, versionId).orElse(null);
        if (version == null) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }

        version.getMetricsByPlatform().clear();
        version.getMetricsByPlatform().putAll(aggregatedMetrics);
        version.getVersionMetadata().setLatestMetricsAggregationDate(Timestamp.from(Instant.now()));
        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);
        return Response.ok().entity(version.getMetricsByPlatform()).build();
    }

    @Override
    public Map<Partner, Metrics> getAggregatedMetrics(String id, String versionId, Optional<User> user) {
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, user);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            throw new CustomWebApplicationException("Invalid entry ID", HttpStatus.SC_BAD_REQUEST);
        }
        checkEntryNotNull(entry);

        Version<?> version = getVersion(entry, versionId).orElse(null);
        if (version == null) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }

        return version.getMetricsByPlatform();
    }

    @Override
    public Response getExecution(String id, String versionId, Partner platform, String executionId, User user) {
        checkPlatformForRole(user, platform);

        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            throw new CustomWebApplicationException("Invalid entry ID", HttpStatus.SC_BAD_REQUEST);
        }
        checkEntryNotNull(entry);

        Version<?> version = getVersion(entry, versionId).orElse(null);
        if (version == null) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }

        MetricsDataS3Client metricsDataS3Client;
        try {
            metricsDataS3Client = new MetricsDataS3Client(metricsConfig.getS3BucketName(), metricsConfig.getS3EndpointOverride());
        } catch (URISyntaxException e) {
            throw new CustomWebApplicationException("Error creating S3 client, could not get execution", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        ExecutionsRequestBodyS3Handler executionsRequestBodyS3Handler = new ExecutionsRequestBodyS3Handler(id, versionId, platform, metricsDataS3Client);
        Optional<ExecutionsFromS3> executionsFromS3 = executionsRequestBodyS3Handler.searchS3ForExecutionId(executionId, true);
        if (executionsFromS3.isPresent()) {
            return Response.ok(executionsFromS3.get().executionsRequestBody()).build();
        } else {
            throw new CustomWebApplicationException(EXECUTION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }
    }

    @Override
    public Response updateExecutionMetrics(String id, String versionId, Partner platform, User owner, String description, ExecutionsRequestBody executions) {
        checkPlatformForRole(owner, platform);
        final long ownerId = owner.getId();

        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        checkEntryNotNull(entry);

        Version<?> version = getVersion(entry, versionId).orElse(null);
        if (version == null) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }

        MetricsDataS3Client metricsDataS3Client;
        try {
            metricsDataS3Client = new MetricsDataS3Client(metricsConfig.getS3BucketName(), metricsConfig.getS3EndpointOverride());
        } catch (URISyntaxException e) {
            throw new CustomWebApplicationException("Error creating S3 client", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        ExecutionsRequestBodyS3Handler executionsRequestBodyS3Handler = new ExecutionsRequestBodyS3Handler(id, versionId, platform, metricsDataS3Client);
        ExecutionsResponseBody executionsResponseBody = new ExecutionsResponseBody();
        List<? extends Execution> executionsToUpdate = Stream.of(executions.getRunExecutions(), executions.getTaskExecutions(), executions.getValidationExecutions())
                .flatMap(List::stream)
                .toList();
        for (Execution executionToUpdate: executionsToUpdate) {
            final String executionId = executionToUpdate.getExecutionId();
            Optional<ExecutionsFromS3> executionsFromS3 = executionsRequestBodyS3Handler.searchS3ForExecutionId(executionId, false);
            if (executionsFromS3.isPresent()) {
                ExecutionsRequestBody executionsRequestBody = executionsFromS3.get().executionsRequestBody();
                executionsRequestBody.updateExecution(executionToUpdate);
                try {
                    executionsRequestBodyS3Handler.createS3ObjectForExecutionsRequestBody(executionsFromS3.get().fileName(), ownerId, description,
                            executionsRequestBody);
                    executionsResponseBody.getExecutionResponses().add(new ExecutionResponse(executionId, HttpStatus.SC_OK));
                } catch (AwsServiceException | SdkClientException | JsonProcessingException e) {
                    LOG.error("{} with ID {}", COULD_NOT_UPDATE_EXECUTION, executionId, e);
                    executionsResponseBody.getExecutionResponses().add(new ExecutionResponse(executionId, HttpStatus.SC_BAD_REQUEST,
                            COULD_NOT_UPDATE_EXECUTION + ": " + e.getMessage()));
                }
            } else {
                executionsResponseBody.getExecutionResponses().add(new ExecutionResponse(executionId, HttpStatus.SC_NOT_FOUND, EXECUTION_NOT_FOUND_ERROR));
            }
        }

        if (executionsResponseBody.getExecutionResponses().stream().anyMatch(executionResponse -> executionResponse.getStatus() == HttpStatus.SC_OK)) {
            version.getVersionMetadata().setLatestMetricsSubmissionDate(Timestamp.from(Instant.now()));
        }

        return Response.status(HttpStatus.SC_MULTI_STATUS).entity(executionsResponseBody).build();
    }

    @Override
    public Response updateAITopic(String id, UpdateAITopicRequest updateAITopicRequest, String version) {
        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty()); // Not providing user to ensure entry is public
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        checkEntryNotNull(entry);

        Version<?> versionReturned = getVersion(entry, version).orElseThrow(
            () -> new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND));

        if (entry.getTopicAI() != null && !entry.getTopicAI().equals(updateAITopicRequest.getAiTopic())) {
            entry.setApprovedAITopic(false); // Topic has changed and user needs to review and approve it again
        }
        entry.setTopicAI(updateAITopicRequest.getAiTopic());
        versionReturned.setAiTopicProcessed(true);
        // Set topic selection to AI if the manual and automatic topics are empty
        if (StringUtils.isEmpty(entry.getTopicManual()) && StringUtils.isEmpty(entry.getTopicAutomatic())) {
            entry.setTopicSelection(TopicSelection.AI);
        }
        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);

        return Response.noContent().build();
    }

    @Override
    public Response getAITopicCandidate(String id) {
        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty()); // Not providing user to ensure entry is public
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        checkEntryNotNull(entry);

        Optional<Version> versionOptional = EntryVersionHelper.determineRepresentativeVersion(entry);
        if (versionOptional.isEmpty()) {
            throw new CustomWebApplicationException(VERSION_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }
        return Response.ok(versionOptional.get().getName()).build();
    }

    @Override
    public Response getAITopicCandidates(int offset, int limit) {
        // Get published entries that don't have any topics
        List<Entry> entriesWithNoTopics = workflowDAO.getPublishedEntriesWithNoTopics(offset, limit);
        List<EntryLiteAndVersionName> aiTopicCandidates = entriesWithNoTopics.stream()
                .map(entry -> {
                    EntryLite entryLite = entry.createEntryLite();
                    String versionCandidateName = EntryVersionHelper.determineRepresentativeVersion(entry)
                            .map(Version::getName)
                            .orElse(""); // Return empty string if there's no representative version
                    return new EntryLiteAndVersionName(entryLite, versionCandidateName);
                })
                .toList();

        long totalCount = workflowDAO.countPublishedEntriesWithNoTopics();
        return Response.ok(aiTopicCandidates).header(X_TOTAL_COUNT, totalCount).build();
    }

    private Entry<?, ?> getEntry(String id, Optional<User> user) throws UnsupportedEncodingException, IllegalArgumentException {
        ToolsApiServiceImpl.ParsedRegistryID parsedID =  new ToolsApiServiceImpl.ParsedRegistryID(id);
        return TOOLS_API_SERVICE_IMPL.getEntry(parsedID, user);
    }

    private void checkEntryNotNull(Entry<?, ?> entry) {
        if (entry == null) {
            throw new CustomWebApplicationException(TOOL_NOT_FOUND_ERROR, HttpStatus.SC_NOT_FOUND);
        }
    }

    private Optional<? extends Version<?>> getVersion(Entry<?, ?> entry, String versionId) {
        Optional<? extends Version<?>> versionOptional = Optional.empty();
        if (entry instanceof Workflow workflow) {
            versionOptional = Optional.ofNullable(workflowVersionDAO.getWorkflowVersionByWorkflowIdAndVersionName(workflow.getId(), versionId));
        } else if (entry instanceof Tool tool) {
            Set<Tag> versions = tool.getWorkflowVersions();
            versionOptional = versions.stream().filter(tag -> tag.getName().equals(versionId)).findFirst();
        }
        return versionOptional;
    }

    private void deleteIndex(String index, RestHighLevelClient restClient) {
        try {
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
            restClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            LOG.warn("Could not delete previous elastic search " + index + " index, not an issue if this is cold start", e);
        }
    }

    /**
     * Checks if the platform is an actual platform and not Partner.ALL
     * @param platform
     */
    private void checkActualPlatform(Partner platform) {
        if (!platform.isActualPartner()) {
            throw new CustomWebApplicationException(INVALID_PLATFORM, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Checks if the user is a platform partner or metrics robot and if the platform they're trying to access is the platform they have permissions for.
     * @param user
     * @param platform
     */
    private void checkPlatformForRole(User user, Partner platform) {
        if (user.isPlatformPartner() && user.getPlatformPartner() != platform
           || user.isMetricsRobot() && user.getMetricsRobotPartner() != platform) {
            throw new CustomWebApplicationException(FORBIDDEN_PLATFORM, HttpStatus.SC_FORBIDDEN);
        }
    }
}
