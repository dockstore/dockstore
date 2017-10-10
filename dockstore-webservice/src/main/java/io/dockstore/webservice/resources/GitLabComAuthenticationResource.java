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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dropwizard.views.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * Created by aduncan on 05/10/16.
 */
@Path("/integration.gitlab.com")
@Api("/integration.gitlab.com")
@Produces(MediaType.TEXT_HTML)
public class GitLabComAuthenticationResource {
    private final String clientID;
    private final String redirectURI;

    public GitLabComAuthenticationResource(String clientID, String redirectURI) {
        this.clientID = clientID;
        this.redirectURI = redirectURI;
    }

    @GET
    @ApiOperation(value = "Display an authorization link for gitlab.com", notes = "This is a stop-gap GUI for displaying a link that allows a user to start the OAuth 2 web flow", response = GitlabComView.class)
    public GitlabComView getView() {
        return new GitlabComView();
    }

    /**
     * @return the clientID
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * @return the redirectURI
     */
    public String getRedirectURI() {
        return redirectURI;
    }

    public class GitlabComView extends View {
        private final GitLabComAuthenticationResource parent;

        public GitlabComView() {
            super("gitlab.com.auth.view.ftl");
            parent = GitLabComAuthenticationResource.this;
        }

        /**
         * @return the parent
         */
        public GitLabComAuthenticationResource getParent() {
            return parent;
        }
    }

}
