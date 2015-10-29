package io.dockstore.webservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.CacheBuilderSpec;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public class DockstoreWebserviceConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @NotEmpty
    private String template;

    @NotEmpty
    private String defaultName = "Stranger";

    @NotEmpty
    private String quayClientID;

    @NotEmpty
    private String githubClientID;

    @NotEmpty
    private String quayRedirectURI;

    @NotEmpty
    private String githubRedirectURI;

    @NotEmpty
    private String githubClientSecret;

    @NotNull
    private CacheBuilderSpec authenticationCachePolicy;

    @NotEmpty
    private String hostName;

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }

    @JsonProperty
    public String getTemplate() {
        return template;
    }

    @JsonProperty
    public void setTemplate(String template) {
        this.template = template;
    }

    @JsonProperty
    public String getDefaultName() {
        return defaultName;
    }

    @JsonProperty
    public void setDefaultName(String name) {
        this.defaultName = name;
    }

    /**
     * @return the quayClientID
     */
    @JsonProperty
    public String getQuayClientID() {
        return quayClientID;
    }

    /**
     * @param quayClientID
     *            the quayClientID to set
     */
    @JsonProperty
    public void setQuayClientID(String quayClientID) {
        this.quayClientID = quayClientID;
    }

    /**
     * @return the quayRedirectURI
     */
    @JsonProperty
    public String getQuayRedirectURI() {
        return quayRedirectURI;
    }

    /**
     * @param quayRedirectURI
     *            the quayRedirectURI to set
     */
    @JsonProperty
    public void setQuayRedirectURI(String quayRedirectURI) {
        this.quayRedirectURI = quayRedirectURI;
    }

    /**
     * @param database
     *            the database to set
     */
    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) {
        this.database = database;
    }

    /**
     * @param httpClient
     *            the httpClient to set
     */
    @JsonProperty("httpClient")
    public void setHttpClientConfiguration(HttpClientConfiguration httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * @return the githubClientID
     */
    @JsonProperty
    public String getGithubClientID() {
        return githubClientID;
    }

    /**
     * @param githubClientID
     *            the githubClientID to set
     */
    @JsonProperty
    public void setGithubClientID(String githubClientID) {
        this.githubClientID = githubClientID;
    }

    /**
     * @return the githubRedirectURI
     */
    @JsonProperty
    public String getGithubRedirectURI() {
        return githubRedirectURI;
    }

    /**
     * @param githubRedirectURI
     *            the githubRedirectURI to set
     */
    @JsonProperty
    public void setGithubRedirectURI(String githubRedirectURI) {
        this.githubRedirectURI = githubRedirectURI;
    }

    /**
     * @return the githubClientSecret
     */
    @JsonProperty
    public String getGithubClientSecret() {
        return githubClientSecret;
    }

    /**
     * @param githubClientSecret
     *            the githubClientSecret to set
     */
    @JsonProperty
    public void setGithubClientSecret(String githubClientSecret) {
        this.githubClientSecret = githubClientSecret;
    }

    public CacheBuilderSpec getAuthenticationCachePolicy() {
        return authenticationCachePolicy;
    }

    public void setAuthenticationCachePolicy(CacheBuilderSpec authenticationCachePolicy) {
        this.authenticationCachePolicy = authenticationCachePolicy;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
}
