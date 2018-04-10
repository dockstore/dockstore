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
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * @author dyuen
 */
@Path("/github.repo")
@Api("/github.repo")
@Produces(MediaType.APPLICATION_JSON)
public class GitHubRepoResource {


    @GET
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can list only the repos they own by default", response = String.class, hidden = true)
    public String listOwned() {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Path("/refreshRepos")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can trigger a sync on the repos they're associated with", response = String.class, hidden = true)
    public String refreshOwned() {
        throw new UnsupportedOperationException();
    }
}
