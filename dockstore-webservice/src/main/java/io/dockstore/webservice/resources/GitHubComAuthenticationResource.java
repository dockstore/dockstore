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

package io.dockstore.webservice.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dropwizard.views.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * @author dyuen
 */
@Path("/integration.github.com")
@Api("/integration.github.com")
@Produces(MediaType.TEXT_HTML)
public class GitHubComAuthenticationResource {
    private final List<String> clientID;
    private final String redirectURI;

    public GitHubComAuthenticationResource(List<String> clientID, String redirectURI) {
        this.clientID = clientID;
        this.redirectURI = redirectURI;
    }

    @GET
    @ApiOperation(value = "Display an authorization link for github.com", notes = "This is a stop-gap GUI for displaying a link that allows a user to start the OAuth 2 web flow", response = GithubComView.class)
    public GithubComView getView() {
        return new GithubComView();
    }

    /**
     * @return the clientID
     */
    public String getClientID() {
        return clientID.get(0);
    }

    /**
     * @return the redirectURI
     */
    public String getRedirectURI() {
        return redirectURI;
    }

    public class GithubComView extends View {
        private final GitHubComAuthenticationResource parent;

        public GithubComView() {
            super("github.com.auth.view.ftl");
            parent = GitHubComAuthenticationResource.this;
        }

        /**
         * @return the parent
         */
        public GitHubComAuthenticationResource getParent() {
            return parent;
        }
    }

}
