/*
 * Copyright (C) 2015 Consonance
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.resources;

import io.dropwizard.views.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author dyuen
 */
@Path("/integration.quay.io")
@Api(value = "/integration.quay.io")
@Produces(MediaType.TEXT_HTML)
public class QuayIOAuthenticationResource {
    private final String clientID;
    private final String redirectURI;

    public QuayIOAuthenticationResource(String clientID, String redirectURI) {
        this.clientID = clientID;
        this.redirectURI = redirectURI;
    }

    @GET
    @ApiOperation(value = "Display an authorization link for quay.io", notes = "More notes about this method", response = QuayIOView.class)
    public QuayIOView getView() {
        return new QuayIOView();
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

    public class QuayIOView extends View {
        private final QuayIOAuthenticationResource parent;

        public QuayIOView() {
            super("quay.io.auth.view.ftl");
            parent = QuayIOAuthenticationResource.this;
        }

        /**
         * @return the parent
         */
        public QuayIOAuthenticationResource getParent() {
            return parent;
        }
    }

}
