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

import static io.openapi.api.impl.ToolsApiServiceImpl.BAD_DECODE_REGISTRY_RESPONSE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.metrics.Execution;
import io.dockstore.webservice.core.metrics.Metrics;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.S3ClientHelper;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.openapi.api.impl.ToolsApiServiceImpl;
import io.swagger.api.impl.ToolsImplCommon;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.ArrayUtils;
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

/**
 * Implementations of methods to return responses containing organization related information
 * @author kcao on 01/03/17.
 *
 */
public class ToolsApiExtendedServiceImpl extends ToolsExtendedApiService {

    public static final int ES_BATCH_INSERT_SIZE = 500;
    public static final String TOOL_NOT_FOUND_ERROR = "Tool not found";
    public static final String VERSION_NOT_FOUND_ERROR = "Version not found";
    public static final String EXECUTION_STATUS_ERROR = "All executions must contain ExecutionStatus";
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiExtendedServiceImpl.class);
    private static final ToolsApiServiceImpl TOOLS_API_SERVICE_IMPL = new ToolsApiServiceImpl();

    private static final String TOOLS_INDEX = ElasticListener.TOOLS_INDEX;
    private static final String WORKFLOWS_INDEX = ElasticListener.WORKFLOWS_INDEX;
    private static final String NOTEBOOKS_INDEX = ElasticListener.NOTEBOOKS_INDEX;
    private static final String COMMA_SEPARATED_INDEXES = String.join(",", ElasticListener.INDEXES);
    private static final int SEARCH_TERM_LIMIT = 256;
    private static final int TOO_MANY_REQUESTS_429 = 429;
    private static final int ELASTICSEARCH_DEFAULT_LIMIT = 15;

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static AppToolDAO appToolDAO = null;
    private static NotebookDAO notebookDAO = null;
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
                checkSearchTermLimit(query);
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
                    if (query != null) {
                        request.setJsonEntity(query);
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
     * Performing a search on the UI sends multiple POST requests. When the search term ("value" key of a wildcard or "include" key in request payload) is large,
     * the POST requests containing these keys will fail.
     * @param query
     */
    protected static void checkSearchTermLimit(String query) {
        if (query != null) {
            JSONObject json = new JSONObject(query);

            try {
                String include = json.getJSONObject("aggs").getJSONObject("autocomplete").getJSONObject("terms").getString("include");
                if (include.length() > SEARCH_TERM_LIMIT) {
                    throw new CustomWebApplicationException("Search request exceeds limit", HttpStatus.SC_REQUEST_TOO_LONG);
                }
            } catch (JSONException ex) { // The request bodies all look pretty different, so it's okay for the exception to get thrown.
                LOG.debug("Couldn't parse search payload request.");
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
                LOG.debug("Couldn't parse search payload request.");
            }
        }
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

        if (entry instanceof Workflow) {
            Workflow workflow = (Workflow)entry;
            Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
            versionOptional = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals(versionId)).findFirst();
        } else if (entry instanceof Tool) {
            Tool tool = (Tool)entry;
            Set<Tag> versions = tool.getWorkflowVersions();
            versionOptional = versions.stream().filter(tag -> tag.getName().equals(versionId)).findFirst();
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
    public Response submitMetricsData(String id, String versionId, Partner platform, User owner, String description, List<Execution> executions) {
        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.of(owner));
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }

        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), TOOL_NOT_FOUND_ERROR).build();
        }

        Optional<? extends Version<?>> version = getVersion(entry, versionId);
        if (version.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), VERSION_NOT_FOUND_ERROR).build();
        }

        // Check that all executions have at least the ExecutionStatus
        if (executions.stream().anyMatch(execution -> execution.getExecutionStatus() == null)) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), EXECUTION_STATUS_ERROR).build();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            String metricsData = mapper.writeValueAsString(executions);
            MetricsDataS3Client metricsDataS3Client = new MetricsDataS3Client(metricsConfig.getS3BucketName(), metricsConfig.getS3EndpointOverride());
            metricsDataS3Client.createS3Object(id, versionId, platform.name(), S3ClientHelper.createFileName(), owner.getId(), description, metricsData);
            return Response.noContent().build();
        } catch (Exception e) {
            LOG.error("Could not submit metrics data", e);
            throw new CustomWebApplicationException("Could not submit metrics data", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public Response setAggregatedMetrics(String id, String versionId, Partner platform, Metrics aggregatedMetrics) {
        // Check that the entry and version exists
        Entry<?, ?> entry;
        try {
            entry = getEntry(id, Optional.empty());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }

        Version<?> version = getVersion(entry, versionId).orElse(null);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), TOOL_NOT_FOUND_ERROR).build();
        } else if (version == null) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(), VERSION_NOT_FOUND_ERROR).build();
        }

        version.getMetricsByPlatform().put(platform, aggregatedMetrics);
        return Response.ok().entity(version.getMetricsByPlatform()).build();
    }

    private Entry<?, ?> getEntry(String id, Optional<User> user) throws UnsupportedEncodingException, IllegalArgumentException {
        ToolsApiServiceImpl.ParsedRegistryID parsedID =  new ToolsApiServiceImpl.ParsedRegistryID(id);
        return TOOLS_API_SERVICE_IMPL.getEntry(parsedID, user);
    }

    private Optional<? extends Version<?>> getVersion(Entry<?, ?> entry, String versionId) {
        Optional<? extends Version<?>> versionOptional = Optional.empty();
        if (entry instanceof Workflow workflow) {
            Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
            versionOptional = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals(versionId)).findFirst();
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
}
