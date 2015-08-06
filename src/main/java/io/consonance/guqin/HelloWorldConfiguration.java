package io.consonance.guqin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

public class HelloWorldConfiguration extends Configuration {
    @NotEmpty
    private String template;

    @NotEmpty
    private String defaultName = "Stranger";

    @NotEmpty
    private String clientID;

    @NotEmpty
    private String redirectURI;

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
     * @return the clientID
     */
    @JsonProperty
    public String getClientID() {
        return clientID;
    }

    /**
     * @param clientID
     *            the clientID to set
     */
    @JsonProperty
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    /**
     * @return the redirectURI
     */
    @JsonProperty
    public String getRedirectURI() {
        return redirectURI;
    }

    /**
     * @param redirectURI
     *            the redirectURI to set
     */
    @JsonProperty
    public void setRedirectURI(String redirectURI) {
        this.redirectURI = redirectURI;
    }
}
