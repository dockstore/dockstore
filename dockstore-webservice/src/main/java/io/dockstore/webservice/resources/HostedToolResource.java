/*
 *    Copyright 2018 OICR
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

import java.util.Date;
import java.util.Set;

import javax.ws.rs.Path;

import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Path("/containers")
public class HostedToolResource extends AbstractHostedEntryResource<Tool, Tag, ToolDAO, TagDAO> {
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;

    public HostedToolResource(UserDAO userDAO, ToolDAO toolDAO, TagDAO tagDAO, FileDAO fileDAO) {
        super(fileDAO, userDAO);
        this.tagDAO = tagDAO;
        this.toolDAO = toolDAO;
    }

    @Override
    protected ToolDAO getEntryDAO() {
        return toolDAO;
    }

    @Override
    protected TagDAO getVersionDAO() {
        return tagDAO;
    }

    @Override
    @ApiOperation(nickname = "createHostedTool", value = "Create a hosted tool", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Create a hosted tool", response = Tool.class)
    public Tool createHosted(User user, String registry, String name, String descriptorType) {
        return super.createHosted(user, registry, name, descriptorType);
    }

    @Override
    protected Tool getEntry(User user, String registry, String name, String descriptorType) {
        Tool tool = new Tool();
        tool.setRegistry(registry);
        tool.setNamespace(user.getUsername());
        tool.setName(name);
        tool.setMode(ToolMode.HOSTED);
        tool.getUsers().add(user);
        return tool;
    }

    @Override
    @ApiOperation(nickname = "editHostedTool", value = "Non-idempotent operation for creating new revisions of hosted tools", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Non-idempotent operation for creating new revisions of hosted tools", response = Tool.class)
    public Tool editHosted(User user, Long entryId, Set<SourceFile> sourceFiles) {
        return super.editHosted(user, entryId, sourceFiles);
    }

    @Override
    protected Tag getVersion(Tool tool) {
        Tag tag = new Tag();
        tag.setCwlPath("/Dockstore.cwl");
        tag.setDockerfilePath("/Dockerfile");
        tag.setAutomated(false);
        tag.setWdlPath("/Dockstore.wdl");
        tag.setReferenceType(Version.ReferenceType.TAG);
        tag.setLastModified(new Date());
        return tag;
    }

    @Override
    @ApiOperation(nickname = "deleteHostedToolVersion", value = "Delete a revision of a hosted tool", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Delete a revision of a hosted tool", response = Tool.class)
    public Tool deleteHostedVersion(User user, Long entryId, String version) {
        Tool tool = super.deleteHostedVersion(user, entryId, version);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tool;
    }

    //TODO: Need to implement this when we extend hosted tool support
    @Override
    protected boolean checkValidVersion(Set<SourceFile> sourceFiles, Tool entry) {
        return true;
    }
}
