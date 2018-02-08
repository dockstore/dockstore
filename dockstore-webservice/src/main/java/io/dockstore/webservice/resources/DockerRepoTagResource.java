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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.VerifyRequest;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.doi.DOIGeneratorFactory;
import io.dockstore.webservice.doi.DOIGeneratorInterface;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Path("/containers")
@Api("containertags")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoTagResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoTagResource.class);
    private final ElasticManager elasticManager;
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;

    public DockerRepoTagResource(ToolDAO toolDAO, TagDAO tagDAO) {
        this.tagDAO = tagDAO;
        this.toolDAO = toolDAO;
        elasticManager = new ElasticManager();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{containerId}/tags")
    @ApiOperation(value = "Get tags  for a container by id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Lists tags for a container. Enter full path (include quay.io in path).", response = Tag.class, responseContainer = "Set")
    public Set<Tag> getTagsByPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        return c.getTags();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Update the tags linked to a container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag", response = Tag.class, responseContainer = "List")
    public Set<Tag> updateTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of modified tags", required = true) List<Tag> tags) {

        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        // create a map for quick lookup
        Map<Long, Tag> mapOfExistingTags = new HashMap<>();
        for (Tag tag : c.getTags()) {
            mapOfExistingTags.put(tag.getId(), tag);
        }

        for (Tag tag : tags) {
            if (mapOfExistingTags.containsKey(tag.getId())) {
                // remove existing copy and add the new one
                Tag existingTag = mapOfExistingTags.get(tag.getId());

                // If any paths have changed then set dirty bit to true
                boolean dirtyBitCheck = new EqualsBuilder().append(existingTag.getCwlPath(), tag.getCwlPath())
                        .append(existingTag.getWdlPath(), tag.getWdlPath()).append(existingTag.getDockerfilePath(), tag.getDockerfilePath())
                        .isEquals();

                if (!dirtyBitCheck) {
                    existingTag.setDirtyBit(true);
                }

                existingTag.updateByUser(tag);
            }
        }
        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        return result.getTags();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Add new tags linked to a container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag", response = Tag.class, responseContainer = "List")
    public Set<Tag> addTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of new tags", required = true) List<Tag> tags) {

        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        for (Tag tag : tags) {
            final long tagId = tagDAO.create(tag);
            Tag byId = tagDAO.findById(tagId);

            // Set dirty bit since this is a manual add
            byId.setDirtyBit(true);

            c.addTag(byId);
        }

        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        return result.getTags();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags/{tagId}")
    @ApiOperation(value = "Delete tag linked to a container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag")
    public Response deleteTags(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tag to delete", required = true) @PathParam("tagId") Long tagId) {

        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        Tag tag = tagDAO.findById(tagId);
        if (tag == null) {
            LOG.error(user.getUsername() + ": could not find tag: " + c.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }

        Set<Tag> listOfTags = c.getTags();

        if (listOfTags.contains(tag)) {
            tag.getSourceFiles().clear();

            if (c.getTags().remove(tag)) {
                elasticManager.handleIndexUpdate(c, ElasticMode.UPDATE);
                return Response.noContent().build();
            } else {
                return Response.serverError().build();
            }
        } else {
            LOG.error(user.getUsername() + ": could not find tag: " + tagId + " in " + c.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/verify/{tagId}")
    @RolesAllowed("admin")
    @ApiOperation(value = "Verify or unverify a version . ADMIN ONLY", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List")
    public Set<Tag> verifyToolTag(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tag to verify.", required = true) @PathParam("tagId") Long tagId,
            @ApiParam(value = "Object containing verification information.", required = true) VerifyRequest verifyRequest) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);
        checkUser(user, tool);

        Tag tag = tagDAO.findById(tagId);
        if (tag == null) {
            LOG.error(user.getUsername() + ": could not find tag: " + tool.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }

        if (verifyRequest.getVerify()) {
            if (Strings.isNullOrEmpty(verifyRequest.getVerifiedSource())) {
                throw new CustomWebApplicationException("A source must be included to verify a tag.", HttpStatus.SC_BAD_REQUEST);
            }
            tag.updateVerified(true, verifyRequest.getVerifiedSource());
        } else {
            tag.updateVerified(false, null);
        }

        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        return result.getTags();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/requestDOI/{tagId}")
    @ApiOperation(value = "Request a DOI for this version of a tool", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List")
    public Set<Tag> requestDOIForToolTag(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Tag to verify.", required = true) @PathParam("tagId") Long tagId) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);
        checkUser(user, tool);

        Tag tag = tagDAO.findById(tagId);
        if (tag == null) {
            LOG.error(user.getUsername() + ": could not find tag: " + tool.getToolPath());
            throw new CustomWebApplicationException("Tag not found.", HttpStatus.SC_BAD_REQUEST);
        }

        if (tag.getDoiStatus() != Version.DOIStatus.CREATED) {
            DOIGeneratorInterface generator = DOIGeneratorFactory.createDOIGenerator();
            generator.createDOIForTool(containerId, tagId);
            tag.setDoiStatus(Version.DOIStatus.REQUESTED);
        }

        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        return result.getTags();
    }
}
