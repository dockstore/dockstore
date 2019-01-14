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
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.Path;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Api("hosted")
@Path("/containers")
public class HostedToolResource extends AbstractHostedEntryResource<Tool, Tag, ToolDAO, TagDAO> {
    private static final Logger LOG = LoggerFactory.getLogger(HostedToolResource.class);
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;

    public HostedToolResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration.LimitConfig limitConfig) {
        super(sessionFactory, permissionsInterface, limitConfig);
        this.tagDAO = new TagDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
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
    @ApiOperation(nickname = "createHostedTool", value = "Create a hosted tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool createHosted(User user, String registry, String name, String descriptorType, String namespace, String entryName) {
        return super.createHosted(user, registry, name, descriptorType, namespace, entryName);
    }

    @Override
    protected Tool getEntry(User user, Registry registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName) {
        Tool tool = new Tool();
        tool.setRegistry(registry.toString());
        tool.setNamespace(namespace);
        tool.setName(name);
        tool.setMode(ToolMode.HOSTED);
        tool.setLastUpdated(new Date());
        tool.setLastModified(new Date());
        tool.setToolname(entryName);
        tool.getUsers().add(user);
        return tool;
    }

    @Override
    @ApiOperation(nickname = "editHostedTool", value = "Non-idempotent operation for creating new revisions of hosted tools.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool editHosted(User user, Long entryId, Set<SourceFile> sourceFiles) {
        return super.editHosted(user, entryId, sourceFiles);
    }

    @Override
    protected void populateMetadata(Set<SourceFile> sourceFiles, Tool entry, Tag tag) {
        for (SourceFile file : sourceFiles) {
            if (file.getPath().equals(tag.getCwlPath()) || file.getPath().equals(tag.getWdlPath())) {
                LOG.info("refreshing metadata based on " + file.getPath() + " from " + tag.getName());
                LanguageHandlerFactory.getInterface(file.getType()).parseWorkflowContent(entry, file.getPath(), file.getContent(), sourceFiles);
            }
        }
    }

    @Override
    protected void checkForDuplicatePath(Tool tool) {
        MutablePair<String, Entry> duplicate = getEntryDAO().findEntryByPath(tool.getToolPath(), false);
        if (duplicate != null) {
            throw new CustomWebApplicationException("A tool already exists with that path. Please change the tool name to something unique.", HttpStatus.SC_BAD_REQUEST);
        }
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
    @ApiOperation(nickname = "deleteHostedToolVersion", value = "Delete a revision of a hosted tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool deleteHostedVersion(User user, Long entryId, String version) {
        Tool tool = super.deleteHostedVersion(user, entryId, version);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tool;
    }

    @Override
    protected boolean checkValidVersion(Set<SourceFile> sourceFiles, Tool entry) {
        boolean isValidCWL = sourceFiles.stream().anyMatch(sf -> Objects.equals(sf.getPath(), "/Dockstore.cwl"));
        boolean isValidWDL = sourceFiles.stream().anyMatch(sf -> Objects.equals(sf.getPath(), "/Dockstore.wdl"));
        boolean hasDockerfile = sourceFiles.stream().anyMatch(sf -> Objects.equals(sf.getPath(), "/Dockerfile"));
        return (isValidCWL || isValidWDL) && hasDockerfile;
    }

    @Override
    protected DescriptorLanguage checkType(String descriptorType) {
        // Descriptor type does not matter for tools
        return null;
    }

    @Override
    protected Registry checkRegistry(String registry) {
        for (Registry registryObject : Registry.values()) {
            if (Objects.equals(registry.toLowerCase(), registryObject.toString())) {
                return registryObject;
            }
        }
        throw new CustomWebApplicationException(registry + " is not a valid registry type", HttpStatus.SC_BAD_REQUEST);
    }
}
