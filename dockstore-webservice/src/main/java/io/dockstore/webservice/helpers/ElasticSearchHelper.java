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

    private ElasticSearchHelper() {
    }

    public static RestClientBuilder restClientBuilder(DockstoreWebserviceConfiguration.ElasticSearchConfig config) {
        final RestClientBuilder builder = RestClient.builder(new HttpHost(config.getHostname(), config.getPort(), getProtocol(config)));
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

    private static String getProtocol(DockstoreWebserviceConfiguration.ElasticSearchConfig config) {
        if (StringUtils.isBlank(config.getProtocol())) {
            return "http";
        } else {
            return config.getProtocol();
        }
    }


}
