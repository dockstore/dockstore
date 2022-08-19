package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class  ElasticSearchHelper implements Managed {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchHelper.class);
    private static RestHighLevelClient restHighLevelClient = null;
    private static final String LOCK_INDEX = "lock";
    private static final String LOCK_DOCUMENT_ID = "locked";

    private DockstoreWebserviceConfiguration.ElasticSearchConfig config;

    public ElasticSearchHelper(DockstoreWebserviceConfiguration.ElasticSearchConfig config) {
        this.config = config;
    }

    public static RestClient restClient() {
        return restHighLevelClient().getLowLevelClient();
    }

    public static synchronized RestHighLevelClient restHighLevelClient() {
        return restHighLevelClient;
    }

    public static boolean doMappingsExist() {
        GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
        try {
            GetMappingsResponse response = restHighLevelClient.indices().getMapping(getMappingsRequest, RequestOptions.DEFAULT);
            return response.mappings().keySet().containsAll(ElasticListener.ALL_INDICES_LIST);
        } catch (Exception e) {
            LOG.error("Could not get Elasticsearch mappings", e);
            return false;
        }
    }

    /**
     * Acquires the Elasticsearch lock by creating a document that is used to indicate if the lock is available or not.
     * If the request to create the document fails, then the document is already exists, and the lock is not available.
     * If the request to create the document succeeds, then the document does not exist and the lock is available.
     * Inspiration taken from: <a href="https://www.elastic.co/guide/en/elasticsearch/guide/current/concurrency-solutions.html#global-lock">...</a>
     * @return boolean indicating if the lock was acquired successfully
     */
    public static boolean acquireLock() {
        RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
        IndexRequest indexRequest = new IndexRequest(LOCK_INDEX).id(LOCK_DOCUMENT_ID).opType(DocWriteRequest.OpType.CREATE).source("{}", XContentType.JSON);
        try {
            client.index(indexRequest, RequestOptions.DEFAULT);
            LOG.info("Acquired Elasticsearch lock");
            return true;
        } catch (Exception e) {
            LOG.error("Unable to acquire Elasticsearch lock", e);
            return false;
        }
    }

    /**
     * Releases the Elasticsearch lock by deleting the document that represents a lock. View acquireLock() for more info
     * @return boolean indicating if the lock was released successfully
     */
    public static boolean releaseLock() {
        RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
        DeleteRequest deleteRequest = new DeleteRequest(LOCK_INDEX).id(LOCK_DOCUMENT_ID);
        try {
            client.delete(deleteRequest, RequestOptions.DEFAULT);
            LOG.info("Released Elasticsearch lock");
            return true;
        } catch (Exception e) {
            LOG.error("Could not release Elasticsearch lock", e);
            return false;
        }
    }

    private RestClientBuilder builder() {
        if (config == null) {
            throw new IllegalStateException("ElasticSearchHelper.config is not set");
        }
        final RestClientBuilder builder = RestClient.builder(new HttpHost(config.getHostname(), config.getPort(), getProtocol()));
        if (StringUtils.isBlank(config.getUser())) {
            return builder;
        } else {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUser(), config.getPassword()));
            return builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                @Override
                public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpAsyncClientBuilder) {
                    return httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }
            });
        }
    }

    private String getProtocol() {
        if (StringUtils.isBlank(config.getProtocol())) {
            return "http";
        } else {
            return config.getProtocol();
        }
    }

    @Override
    public void start() throws Exception {
        if (StringUtils.isNotBlank(config.getHostname())) {
            restHighLevelClient = new RestHighLevelClient(builder());
        }
    }

    @Override
    public void stop() throws Exception {
        if (restHighLevelClient != null) {
            restHighLevelClient.close();
        }
    }
}
