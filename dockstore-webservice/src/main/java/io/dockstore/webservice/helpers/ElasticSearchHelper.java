package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

public final class  ElasticSearchHelper {

    private static DockstoreWebserviceConfiguration.ElasticSearchConfig config;
    private static RestClientBuilder restClientBuilder;

    private ElasticSearchHelper() {
    }

    public static void setConfig(DockstoreWebserviceConfiguration.ElasticSearchConfig config) {
        ElasticSearchHelper.config = config;
    }

    public static synchronized RestClientBuilder restClientBuilder() {
        if (restClientBuilder == null) {
            if (config == null) {
                throw new IllegalStateException("ElasticSearchHelper.config is not set");
            }
            final RestClientBuilder builder = RestClient.builder(new HttpHost(config.getHostname(), config.getPort(), getProtocol()));
            if (StringUtils.isBlank(config.getUser())) {
                restClientBuilder = builder;
            } else {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUser(), config.getPassword()));
                restClientBuilder =  builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpAsyncClientBuilder) {
                        return httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                });
            }
        }
        return restClientBuilder;
    }

    private static String getProtocol() {
        if (StringUtils.isBlank(config.getProtocol())) {
            return "http";
        } else {
            return config.getProtocol();
        }
    }


}
