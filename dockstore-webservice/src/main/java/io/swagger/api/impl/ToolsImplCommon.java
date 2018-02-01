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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import io.dockstore.webservice.DockstoreWebserviceApplication;
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
import io.swagger.model.ToolTests;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.core.SourceFile.FileType.DOCKERFILE;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_WDL;

/**
 * Utility methods for interacting with GA4GH tools and workflows
 * Created by kcao on 01/03/17.
 */
public final class ToolsImplCommon {
    private static final Logger LOG = LoggerFactory.getLogger(ToolsImplCommon.class);
    private static final String GITHUB_PREFIX = "git@github.com:";
    private static final String BITBUCKET_PREFIX = "git@bitbucket.org:";

    private ToolsImplCommon() { }

    /**
     * Build a descriptor and attach it to a version
     *
     * @param url  url to set for the descriptor
     * @param file a file with content for the descriptor
     */
    private static ToolDescriptor buildSourceFile(String url, SourceFile file) {
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
     * This converts a Dockstore's SourceFile to a GA4GH ToolDescriptor
     *
     * @param sourceFile The Dockstore SourceFile
     * @return The converted GA4GH ToolDescriptor
     */
    static ToolDescriptor sourceFileToToolDescriptor(SourceFile sourceFile) {
        ToolDescriptor toolDescriptor = new ToolDescriptor();
        toolDescriptor.setDescriptor(sourceFile.getContent());
        toolDescriptor.setUrl(sourceFile.getPath());
        if (sourceFile.getType().equals(SourceFile.FileType.DOCKSTORE_CWL)) {
            toolDescriptor.setType(ToolDescriptor.TypeEnum.CWL);
        } else if (sourceFile.getType().equals(SourceFile.FileType.DOCKSTORE_WDL)) {
            toolDescriptor.setType(ToolDescriptor.TypeEnum.WDL);
        } else {
            LOG.error("This source file is not a recognized descriptor.");
            return null;
        }
        return toolDescriptor;
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
    public static Pair<Tool, Table<String, SourceFile.FileType, Object>> convertEntryToTool(Entry container,
            DockstoreWebserviceConfiguration config) {
        Table<String, SourceFile.FileType, Object> fileTable = HashBasedTable.create();

        String url;
        String newID = getNewId(container);
        if (newID == null) {
            return null;
        } else {
            try {
                String baseURL = baseURL(config);
                url = getUrl(newID, baseURL);
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
        }
        // TODO: hook this up to a type field in our DB?
        io.swagger.model.Tool tool = new io.swagger.model.Tool();
        tool = setGeneralToolInfo(tool, container);
        tool.setId(newID);
        tool.setUrl(url);

        Set inputVersions;
        // tool specific
        if (container instanceof io.dockstore.webservice.core.Tool) {
            io.dockstore.webservice.core.Tool dockstoreTool = (io.dockstore.webservice.core.Tool)container;

            // The name is composed of the repository name and then the optional toolname split with a '/'
            String name = dockstoreTool.getName();
            String toolName = dockstoreTool.getToolname();
            String returnName = constructName(Arrays.asList(name, toolName));
            tool.setToolname(returnName);
            tool.setOrganization(dockstoreTool.getNamespace());
            inputVersions = ((io.dockstore.webservice.core.Tool)container).getTags();
        } else if (container instanceof Workflow) {
            // workflow specific
            Workflow workflow = (Workflow)container;

            // The name is composed of the repository name and then the optional toolname split with a '/'
            String name = workflow.getRepository();
            String workflowName = workflow.getWorkflowName();
            String returnName = constructName(Arrays.asList(name, workflowName));
            tool.setToolname(returnName);
            tool.setOrganization(workflow.getOrganization());
            inputVersions = ((Workflow)container).getWorkflowVersions();
        } else {
            LOG.error("Unrecognized container type - neither tool or workflow: " + container.getId());
            return null;
        }

        // handle verified information
        tool = setVerified(tool, inputVersions);
        for (Version version : (Set<Version>)inputVersions) {
            // tags with no names make no sense here
            // also hide hidden tags
            if (version.getName() == null || version.isHidden()) {
                continue;
            }
            if (version instanceof Tag && ((Tag)version).getImageId() == null) {
                continue;
            }

            ToolVersion toolVersion = new ToolVersion();
            try {
                toolVersion = setGeneralToolVersionInfo(url, toolVersion, version);
            } catch (UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            toolVersion.setId(tool.getId() + ":" + version.getName());
            String urlBuilt;
            String gitUrl = container.getGitUrl();
            if (gitUrl.startsWith(GITHUB_PREFIX)) {
                urlBuilt = extractHTTPPrefix(gitUrl, version.getReference(), GITHUB_PREFIX, "https://raw.githubusercontent.com/");
            } else if (gitUrl.startsWith(BITBUCKET_PREFIX)) {
                urlBuilt = extractHTTPPrefix(gitUrl, version.getReference(), BITBUCKET_PREFIX, "https://bitbucket.org/");
            } else {
                LOG.error("Found a git url neither from BitBucket or GitHub " + gitUrl);
                urlBuilt = null;
            }

            final Set<SourceFile> sourceFiles = version.getSourceFiles();
            for (SourceFile file : sourceFiles) {
                if (version instanceof Tag) {
                    switch (file.getType()) {
                    case DOCKERFILE:
                        ToolDockerfile dockerfile = new ToolDockerfile();
                        dockerfile.setDockerfile(file.getContent());
                        dockerfile.setUrl(urlBuilt + ((Tag)version).getDockerfilePath());
                        toolVersion.setDockerfile(true);
                        fileTable.put(version.getName(), DOCKERFILE, dockerfile);
                        break;
                    case DOCKSTORE_CWL:
                        if (((Tag)version).getCwlPath().equalsIgnoreCase(file.getPath())) {
                            toolVersion.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                            fileTable.put(version.getName(), DOCKSTORE_CWL, buildSourceFile(urlBuilt + ((Tag)version).getCwlPath(), file));
                        }
                        break;
                    case DOCKSTORE_WDL:
                        toolVersion.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.WDL);
                        fileTable.put(version.getName(), DOCKSTORE_WDL, buildSourceFile(urlBuilt + ((Tag)version).getWdlPath(), file));
                        break;
                    default:
                        // Unhandled file type is apparently ignored
                        break;
                    }
                } else if (version instanceof WorkflowVersion) {
                    switch (file.getType()) {
                    case DOCKSTORE_CWL:
                        // get the "main" workflow file
                        if (((WorkflowVersion)version).getWorkflowPath().equalsIgnoreCase(file.getPath())) {
                            toolVersion.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.CWL);
                            fileTable.put(version.getName(), DOCKSTORE_CWL,
                                    buildSourceFile(urlBuilt + ((WorkflowVersion)version).getWorkflowPath(), file));
                        }
                        break;
                    case DOCKSTORE_WDL:
                        toolVersion.addDescriptorTypeItem(ToolVersion.DescriptorTypeEnum.WDL);
                        fileTable.put(version.getName(), DOCKSTORE_WDL,
                                buildSourceFile(urlBuilt + ((WorkflowVersion)version).getWorkflowPath(), file));
                        break;
                    default:
                        // Unhandled file type is apparently ignored
                        break;
                    }
                }
            }

            // ensure that descriptor is non-null before adding to list
            if (!toolVersion.getDescriptorType().isEmpty()) {
                // do some clean-up
                toolVersion.setMetaVersion(String.valueOf(version.getLastModified() != null ? version.getLastModified() : new Date(0)));
                final List<ToolVersion.DescriptorTypeEnum> descriptorType = toolVersion.getDescriptorType();
                if (!descriptorType.isEmpty()) {
                    EnumSet<ToolVersion.DescriptorTypeEnum> set = EnumSet.copyOf(descriptorType);
                    toolVersion.setDescriptorType(Lists.newArrayList(set));
                }
                tool.getVersions().add(toolVersion);
            }
        }
        return new ImmutablePair<>(tool, fileTable);
    }

    /**
     * Construct escaped ID and then the URL of the Tool
     *
     * @param newID  The ID of the Tool
     * @param baseURL The base URL for the tools endpoint
     * @return The URL of the Tool
     * @throws UnsupportedEncodingException When URL encoding has failed
     */
    private static String getUrl(String newID, String baseURL)
            throws UnsupportedEncodingException {
        String escapedID = URLEncoder.encode(newID, StandardCharsets.UTF_8.displayName());
        return baseURL + escapedID;
    }

    /**
     * Get baseURL from DockstoreWebServiceConfiguration
     * @param config    The DockstoreWebServiceConfiguration
     * @return          The baseURL for GA4GH tools endpoint
     * @throws URISyntaxException   When URI building goes wrong
     */
    private static String baseURL(DockstoreWebserviceConfiguration config) throws URISyntaxException {
        URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()),
                DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/", null, null);
        return uri.toString();
    }

    /**
     * Sets whether the Tool is verified or not based on the version from Dockstore (Tags or WorkflowVersions)
     *
     * @param tool     The Tool to be modified
     * @param versions The Dockstore versions (Tags or WorkflowVersions)
     * @return The modified Tool with verified set
     */
    private static Tool setVerified(Tool tool, Set<Version> versions) {
        tool.setVerified(versions.stream().anyMatch(Version::isVerified));
        final List<String> collect = versions.stream().filter(Version::isVerified).map(Version::getVerifiedSource)
                .collect(Collectors.toList());
        Gson gson = new Gson();
        Collections.sort(collect);
        tool.setVerifiedSource(Strings.nullToEmpty(gson.toJson(collect)));
        return tool;
    }

    /**
     * Gets the new ID of the Tool
     *
     * @param container The Dockstore Entry (Tool or Workflow)
     * @return The new ID of the Tool
     */
    private static String getNewId(Entry container) {
        if (container instanceof io.dockstore.webservice.core.Tool) {
            return ((io.dockstore.webservice.core.Tool)container).getToolPath();
        } else if (container instanceof Workflow) {
            return "#workflow/" + ((Workflow)container).getPath();
        } else {
            LOG.error("Could not construct URL for our container with id: " + container.getId());
            return null;
        }
    }

    /**
     * Set most of the GA4GH's ToolVersion information that is not based on the Dockstore source files
     *
     * @param url         Base URL of the tool
     * @param toolVersion The ToolVersion that will be modified
     * @param version     The Dockstore Version (Tag or WorkflowVersion)
     * @return The modified ToolVersion
     * @throws UnsupportedEncodingException When URL encoding has failed
     */
    private static ToolVersion setGeneralToolVersionInfo(String url, ToolVersion toolVersion, Version version)
            throws UnsupportedEncodingException {
        String globalVersionId;
        globalVersionId = url + "/versions/" + URLEncoder.encode(version.getName(), StandardCharsets.UTF_8.displayName());
        toolVersion.setUrl(globalVersionId);
        toolVersion.setName(version.getName());
        toolVersion.setVerified(version.isVerified());
        toolVersion.setVerifiedSource(Strings.nullToEmpty(version.getVerifiedSource()));
        toolVersion.setDockerfile(false);

        // Set image if it's a DockstoreTool, otherwise make it empty string (for now)
        if (version instanceof Tag) {
            Tag tag = (Tag)version;
            toolVersion.setImage(tag.getImageId());
        } else {
            // TODO: Modify mapper to ignore null-value properties during serialization for specific endpoint(s)
            toolVersion.setImage("");
        }
        return toolVersion;
    }

    /**
     * Set most of the GA4GH's Tool information that is not dependant on Dockstore's Tags or WorkflowVersions
     *
     * @param tool      The GA4GH Tool that will be modified
     * @param container The Dockstore Tool or Workflow
     * @return The modified Tool
     */
    private static Tool setGeneralToolInfo(Tool tool, Entry container) {
        // Set author
        if (container.getAuthor() == null) {
            tool.setAuthor("Unknown author");
        } else {
            tool.setAuthor(container.getAuthor());
        }

        // Set meta-version
        tool.setMetaVersion(container.getLastUpdated() != null ? container.getLastUpdated().toString() : new Date(0).toString());

        // Set type
        ToolClass type = container instanceof io.dockstore.webservice.core.Tool ? ToolClassesApiServiceImpl.getCommandLineToolClass()
                : ToolClassesApiServiceImpl.getWorkflowClass();
        tool.setToolclass(type);

        // Set signed.  Signed is currently not supported
        tool.setSigned(false);

        // Set description
        tool.setDescription(container.getDescription() != null ? container.getDescription() : "");
        return tool;
    }

    /**
     * Construct the workflow/tool full name
     * @param strings   The components that make up the full name (repository name + optional workflow/tool name)
     * @return  The full workflow/tool name
     */
    private static String constructName(List<String> strings) {
        // The name is composed of the repository name and then the optional workflowname split with a '/'
        StringJoiner joiner = new StringJoiner("/");
        for (String string : strings) {
            if (!Strings.isNullOrEmpty(string)) {
                joiner.add(string);
            }
        }
        return joiner.toString();
    }

    /**
     * Converts a Dockstore SourceFile to GA4GH ToolTests
     * @param sourceFile  The Dockstore SourceFile to convert
     * @return      The resulting GA4GH ToolTests
     */
    static ToolTests sourceFileToToolTests(SourceFile sourceFile) {
        SourceFile.FileType type = sourceFile.getType();
        if (!type.equals(SourceFile.FileType.WDL_TEST_JSON) && !type.equals(SourceFile.FileType.CWL_TEST_JSON)) {
            LOG.error("This source file is not a recognized test file.");
        }
        ToolTests toolTests = new ToolTests();
        toolTests.setUrl(sourceFile.getPath());
        toolTests.setTest(sourceFile.getContent());
        return toolTests;
    }
}
