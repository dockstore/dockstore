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
 * This resource is a stop-gap GUI for web service developers to integrate with BitBucket
 *
 * @author xliu
 */
@Path("/integration.bitbucket.org")
@Api("/integration.bitbucket.org")
@Produces(MediaType.TEXT_HTML)
public class BitbucketOrgAuthenticationResource {
    private final String clientID;

    public BitbucketOrgAuthenticationResource(String clientID) {
        this.clientID = clientID;
        // this.redirectURI = redirectURI;
    }

    @GET
    @ApiOperation(value = "Display an authorization link for bitbucket.org", notes = "This is a stop-gap GUI for displaying a link that allows a user to start the OAuth 2 web flow", response = BitbucketOrgView.class)
    public BitbucketOrgView getView() {
        return new BitbucketOrgView();
    }

    /**
     * @return the clientID
     */
    public String getClientID() {
        return clientID;
    }

    public class BitbucketOrgView extends View {
        private final BitbucketOrgAuthenticationResource parent;

        public BitbucketOrgView() {
            super("bitbucket.org.auth.view.ftl");
            parent = BitbucketOrgAuthenticationResource.this;
        }

        /**
         * @return the parent
         */
        public BitbucketOrgAuthenticationResource getParent() {
            return parent;
        }
    }
}
