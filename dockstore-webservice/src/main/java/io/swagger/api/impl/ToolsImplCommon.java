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
package io.swagger.api.impl;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.swagger.model.Tool;
import io.swagger.model.ToolClass;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.dockstore.webservice.core.SourceFile.FileType.DOCKERFILE;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_WDL;

/**
 * Utility methods for interacting with GA4GH tools and workflows
 * Created by kcao on 01/03/17.
 */
public final class ToolsImplCommon {
    private static ToolsImplCommon instance = null;
    private static final Logger LOG = LoggerFactory.getLogger(ToolsImplCommon.class);

    private ToolsImplCommon() {}

    /**
     * Build a descriptor and attach it to a version
     *
     * @param url  url to set for the descriptor
     * @param file a file with content for the descriptor
     */
    public static ToolDescriptor buildSourceFile(String url, SourceFile file) {
        ToolDescriptor descriptor = new ToolDescriptor();
        if (file.getType() == DOCKSTORE_CWL) {
            descriptor.setType(ToolDescriptor.TypeEnum.CWL);
        } else if (file.getType() == DOCKSTORE_WDL) {
            descriptor.setType(ToolDescriptor.TypeEnum.WDL);
        }
        descriptor.setDescriptor(file.getContent());

        List<String> splitPathList = Lists.newArrayList(url.split("/"));
        splitPathList.remove(splitPathList.size() - 1);
        splitPathList.add(StringUtils.stripStart(file.getPath(), "/"));
        final String join = Joiner.on("/").join(splitPathList);

        descriptor.setUrl(join);
        return descriptor;
    }

    /**
     * @param gitUrl       The git formatted url for the repo
     * @param reference    the git tag or branch
     * @param githubPrefix the prefix for the git formatted url to strip out
     * @param builtPrefix  the prefix to use to start the extracted prefix
     * @return the prefix to access these files
     */
    private static String extractHTTPPrefix(String gitUrl, String reference, String githubPrefix, String builtPrefix) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(builtPrefix);
        final String substring = gitUrl.substring(githubPrefix.length(), gitUrl.lastIndexOf(".git"));
        urlBuilder.append(substring).append('/').append(reference);
        return urlBuilder.toString();
    }

    /**
     * Convert our Tool object to a standard Tool format
     *
     * @param container our data object
     * @return standardised data object
     */
     public static Pair<Tool, Table<String, SourceFile.FileType, Object>> convertContainer2Tool(Entry container, DockstoreWebserviceConfiguration config) {
        Table<String, SourceFile.FileType, Object> fileTable = HashBasedTable.create();
        String globalId;
        // TODO: properly pass this information
        String newID;
        try {
            // construct escaped ID
            if (container instanceof io.dockstore.webservice.core.Tool) {
                newID = ((io.dockstore.webservice.core.Tool)container).getToolPath();
            } else if (container instanceof Workflow) {
                newID = "#workflow/" + ((Workflow)container).getPath();
            } else {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }

            String escapedID = URLEncoder.encode(newID, StandardCharsets.UTF_8.displayName());
            URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/api/ga4gh/v1/tools/",
                    null, null);
            globalId = uri.toString() + escapedID;
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            LOG.error("Could not construct URL for our container with id: " + container.getId());
            return null;
        }
        // TODO: hook this up to a type field in our DB?
        ToolClass type = container instanceof io.dockstore.webservice.core.Tool ? ToolClassesApiServiceImpl.getCommandLineToolClass()
                : ToolClassesApiServiceImpl.getWorkflowClass();

        io.swagger.model.Tool tool = new io.swagger.model.Tool();
        if (container.getAuthor() == null) {
            tool.setAuthor("Unknown author");
        } else {
            tool.setAuthor(container.getAuthor());
        }

        tool.setDescription(container.getDescription());
        tool.setMetaVersion(container.getLastUpdated() != null ? container.getLastUpdated().toString() : new Date(0).toString());
        tool.setToolclass(type);
        tool.setId(newID);
        tool.setUrl(globalId);

        // tool specific
        if (container instanceof io.dockstore.webservice.core.Tool) {
            io.dockstore.webservice.core.Tool inputTool = (io.dockstore.webservice.core.Tool)container;
            tool.setToolname(inputTool.getToolname());
            tool.setOrganization(inputTool.getNamespace());
            tool.setToolname(inputTool.getName());
        }
        // workflow specific
        if (container instanceof Workflow) {
            Workflow inputTool = (Workflow)container;
            tool.setToolname(inputTool.getPath());
            tool.setOrganization(inputTool.getOrganization());
            tool.setToolname(inputTool.getWorkflowName());
        }

        // TODO: contains has no counterpart in our DB
        // setup versions as well
        Set inputVersions;
        if (container instanceof io.dockstore.webservice.core.Tool) {
            inputVersions = ((io.dockstore.webservice.core.Tool)container).getTags();
        } else {
            inputVersions = ((Workflow)container).getWorkflowVersions();
        }

        // handle verified information
        tool.setVerified(((Set<Version>)inputVersions).stream().anyMatch(Version::isVerified));
        final List<String> collect = ((Set<Version>)inputVersions).stream().filter(Version::isVerified).map(Version::getVerifiedSource)
                .collect(Collectors.toList());
        Gson gson = new Gson();
        tool.setVerifiedSource(Strings.nullToEmpty(gson.toJson(collect)));

        for (Version inputVersion : (Set<Version>)inputVersions) {

            // tags with no names make no sense here
            // also hide hidden tags
            if (inputVersion.getName() == null || inputVersion.isHidden()) {
                continue;
            }
            if (inputVersion instanceof Tag && ((Tag)inputVersion).getImageId() == null) {
                continue;
            }

            ToolVersion version = new ToolVersion();
            // version id
            String globalVersionId;
            try {
                globalVersionId = globalId + "/versions/" + URLEncoder.encode(inputVersion.getName(), StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            version.setUrl(globalVersionId);

            version.setId(tool.getId() + ":" + inputVersion.getName());

            version.setName(inputVersion.getName());

            version.setVerified(inputVersion.isVerified());
            version.setVerifiedSource(Strings.nullToEmpty(inputVersion.getVerifiedSource()));
            version.setDockerfile(false);

            String urlBuilt;
            final String githubPrefix = "git@github.com:";
            final String bitbucketPrefix = "git@bitbucket.org:";
            if (container.getGitUrl().startsWith(githubPrefix)) {
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), inputVersion.getReference(), githubPrefix,
                        "https://raw.githubusercontent.com/");
            } else if (container.getGitUrl().startsWith(bitbucketPrefix)) {
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), inputVersion.getReference(), bitbucketPrefix, "https://bitbucket.org/");
            } else {
                LOG.error("Found a git url neither from bitbucket or github " + container.getGitUrl());
                urlBuilt = null;
            }

            final Set<SourceFile> sourceFiles = inputVersion.getSourceFiles();
            for (SourceFile file : sourceFiles) {
                if (inputVersion instanceof Tag) {
                    switch (file.getType()) {
                        case DOCKERFILE:
                            ToolDockerfile dockerfile = new ToolDockerfile();
                            dockerfile.setDockerfile(file.getContent());
                            dockerfile.setUrl(urlBuilt + ((Tag)inputVersion).getDockerfilePath());
                            version.setDockerfile(true);
                            fileTable.put(inputVersion.getName(), DOCKERFILE, dockerfile);
                            break;
                        case DOCKSTORE_CWL:
                            if (((Tag)inputVersion).getCwlPath().equalsIgnoreCase(file.getPath())) {
                                version.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                                fileTable.put(inputVersion.getName(), DOCKSTORE_CWL,
                                        buildSourceFile(urlBuilt + ((Tag)inputVersion).getCwlPath(), file));
                            }
                            break;
                        case DOCKSTORE_WDL:
                            version.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                            fileTable.put(inputVersion.getName(), DOCKSTORE_WDL,
                                    buildSourceFile(urlBuilt + ((Tag)inputVersion).getWdlPath(), file));
                            break;
                    }
                } else if (inputVersion instanceof WorkflowVersion) {
                    switch (file.getType()) {
                        case DOCKSTORE_CWL:
                            // get the "main" workflow file
                            if (((WorkflowVersion)inputVersion).getWorkflowPath().equalsIgnoreCase(file.getPath())) {
                                version.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                                fileTable.put(inputVersion.getName(), DOCKSTORE_CWL,
                                        buildSourceFile(urlBuilt + ((WorkflowVersion)inputVersion).getWorkflowPath(), file));
                            }
                            break;
                        case DOCKSTORE_WDL:
                            version.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                            fileTable.put(inputVersion.getName(), DOCKSTORE_WDL,
                                    buildSourceFile(urlBuilt + ((WorkflowVersion)inputVersion).getWorkflowPath(), file));
                            break;
                    }
                }
            }
            if (container instanceof io.dockstore.webservice.core.Tool) {
                version.setImage(((io.dockstore.webservice.core.Tool)container).getPath() + ":" + inputVersion.getName());
            }
            // ensure that descriptor is non-null before adding to list
            if (!version.getDescriptorType().isEmpty()) {
                // do some clean-up
                version.setMetaVersion(
                        String.valueOf(inputVersion.getLastModified() != null ? inputVersion.getLastModified() : new Date(0)));
                final List<ToolVersion.DescriptorTypeEnum> descriptorType = version.getDescriptorType();
                if (!descriptorType.isEmpty()) {
                    EnumSet<ToolVersion.DescriptorTypeEnum> set = EnumSet.copyOf(descriptorType);
                    version.setDescriptorType(Lists.newArrayList(set));
                }
                tool.getVersions().add(version);
            }

        }
        return new ImmutablePair<>(tool, fileTable);
    }
}
