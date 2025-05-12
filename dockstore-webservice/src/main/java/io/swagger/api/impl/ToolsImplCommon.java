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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.EntryTypeMetadata;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerInterface.DockerSpecifier;
import io.openapi.api.impl.ToolsApiServiceImpl;
import io.openapi.api.impl.ToolsApiServiceImpl.EmptyImageType;
import io.openapi.model.Checksum;
import io.openapi.model.DescriptorType;
import io.openapi.model.ExtendedFileWrapper;
import io.openapi.model.FileWrapper;
import io.openapi.model.ImageData;
import io.openapi.model.ImageType;
import io.openapi.model.Tool;
import io.openapi.model.ToolVersion;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.http.HttpStatus;
import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for interacting with GA4GH tools and workflows
 * Created by kcao on 01/03/17.
 */
public final class ToolsImplCommon {
    public static final String WORKFLOW_PREFIX = "#workflow";
    public static final String SERVICE_PREFIX = "#service";
    public static final String NOTEBOOK_PREFIX = "#notebook";
    public static final String DOCKER_IMAGE_SHA_TYPE_FOR_TRS = "sha-256";
    private static final Logger LOG = LoggerFactory.getLogger(ToolsImplCommon.class);

    private ToolsImplCommon() { }



    /**
     * This converts a Dockstore's SourceFile to a GA4GH ToolDescriptor
     *
     * @param url clean url with no conversion
     * @param sourceFile The Dockstore SourceFile
     * @param selfURL A URL to this file using the relative files endpoint
     * @return The converted GA4GH ToolDescriptor paired with the raw content
     */
    public static ExtendedFileWrapper sourceFileToToolDescriptor(String url, String selfURL, SourceFile sourceFile) {
        ExtendedFileWrapper toolDescriptor = new ExtendedFileWrapper();
        convertToTRSChecksums(sourceFile);
        toolDescriptor.setChecksum(convertToTRSChecksums(sourceFile));
        toolDescriptor.setContent(sourceFile.getContent());
        toolDescriptor.setUrl(url);
        toolDescriptor.setOriginalFile(sourceFile);
        toolDescriptor.setImageType(new EmptyImageType());
        toolDescriptor.setDockstoreAbsolutePath(MoreObjects.firstNonNull(sourceFile.getAbsolutePath(), ""));
        toolDescriptor.setDockstoreSelfUrl(MoreObjects.firstNonNull(selfURL, ""));
        return toolDescriptor;
    }

    public static Tool convertEntryToTool(Entry<?, ?> container, DockstoreWebserviceConfiguration config) {
        return convertEntryToTool(container, config, false);
    }

    /**
     * Convert our Tool object to a standard Tool format
     *
     * @param container our data object
     * @return standardised data object
     */
    public static Tool convertEntryToTool(Entry<?, ?> container, DockstoreWebserviceConfiguration config, boolean showHiddenTags) {
        String newID = getNewId(container);
        boolean isDockstoreTool;
        String url = getUrlFromId(config, newID);
        if (url == null) {
            return null;
        }
        // TODO: hook this up to a type field in our DB?
        Tool tool = new Tool();
        setGeneralToolInfo(tool, container);
        tool.setId(newID);
        tool.setUrl(url);
        String checkerWorkflowPath = getCheckerWorkflowPath(config, container);
        checkerWorkflowPath = (checkerWorkflowPath == null) ? "" : checkerWorkflowPath;
        tool.setCheckerUrl(checkerWorkflowPath);
        boolean hasChecker = !(tool.getCheckerUrl().isEmpty() || tool.getCheckerUrl() == null);
        tool.setHasChecker(hasChecker);
        Set<? extends Version<?>> inputVersions;
        // tool specific
        io.dockstore.webservice.core.Tool castedContainer = null;
        if (container instanceof io.dockstore.webservice.core.Tool) {
            isDockstoreTool = true;
            castedContainer = (io.dockstore.webservice.core.Tool)container;
            // The name is composed of the repository name and then the optional toolname split with a '/'
            String name = castedContainer.getName();
            String toolName = castedContainer.getToolname();
            String returnName = constructName(Arrays.asList(name, toolName));
            tool.setName(returnName);
            tool.setOrganization(castedContainer.getNamespace());
            inputVersions = castedContainer.getWorkflowVersions();
        } else if (container instanceof Workflow workflow) {
            isDockstoreTool = false;
            // workflow specific

            // The name is composed of the repository name and then the optional toolname split with a '/'
            String name = workflow.getRepository();
            String workflowName = workflow.getWorkflowName();
            String returnName = constructName(Arrays.asList(name, workflowName));
            tool.setName(returnName);
            tool.setOrganization(workflow.getOrganization());
            inputVersions = ((Workflow)container).getWorkflowVersions();
        } else {
            LOG.error("Unrecognized container type - neither tool or workflow: " + container.getId());
            return null;
        }
        tool.setAliases(new ArrayList<>(container.getAliases().keySet()));

        for (Version<?> version : inputVersions) {
            if (shouldHideToolVersion(version, showHiddenTags, container.isHosted())) {
                continue;
            }

            ToolVersion toolVersion = new ToolVersion();

            toolVersion.setAuthor(MoreObjects.firstNonNull(toolVersion.getAuthor(), Lists.newArrayList()));
            //TODO: would hook up identified tools that form workflows here
            toolVersion.setIncludedApps(MoreObjects.firstNonNull(toolVersion.getIncludedApps(), Lists.newArrayList()));

            toolVersion.setSigned(false);

            try {
                if (!version.getAuthors().isEmpty()) {
                    toolVersion.setAuthor(version.getAuthors().stream().map(Author::getName).toList());
                } else if (!container.getAuthors().isEmpty()) {
                    toolVersion.setAuthor(container.getAuthors().stream().map(Author::getName).toList());
                }
            } catch (ObjectNotFoundException e) {
                // TODO the filter interacts poorly with the getAuthors call here (which needs the default version), not sure how it worked before anyway
                LOG.error("version filter clashed with geetAuthors", e);
            }

            toolVersion.setImages(MoreObjects.firstNonNull(toolVersion.getImages(), Lists.newArrayList()));
            if (container instanceof io.dockstore.webservice.core.Tool) {
                processImageDataForToolVersion(castedContainer, (Tag)version, toolVersion);
            }

            toolVersion.setIsProduction(version.isFrozen());
            if (toolVersion.isIsProduction()) {
                List<ImageData> trsImages = processImageDataForWorkflowVersions(version);
                toolVersion.getImages().addAll(trsImages);
            }

            try {
                toolVersion = setGeneralToolVersionInfo(url, toolVersion, version);
            } catch (UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            toolVersion.setId(tool.getId() + ":" + version.getName());

            final Set<SourceFile> sourceFiles = version.getSourceFiles();
            for (SourceFile file : sourceFiles) {
                Optional<DescriptorType> descriptorType = getDescriptorTypeFromFileType(file.getType());
                if (descriptorType.isPresent()) {
                    toolVersion.addDescriptorTypeItem(descriptorType.get());
                } else {
                    if (file.getType() == FileType.DOCKERFILE) {
                        toolVersion.setContainerfile(true);
                    }
                    // Unhandled file type is apparently ignored
                }
            }

            toolVersion.setDescriptorType(MoreObjects.firstNonNull(toolVersion.getDescriptorType(), Lists.newArrayList()));
            // ensure that descriptor is non-null before adding to list
            if (!toolVersion.getDescriptorType().isEmpty()) {
                // do some clean-up
                if (isDockstoreTool) {
                    Tag castedTag = (Tag)version;
                    toolVersion.setMetaVersion(String.valueOf(castedTag.getLastBuilt() != null ? castedTag.getLastBuilt() : new Date(0)));
                } else {
                    WorkflowVersion castedWorkflowVersion = (WorkflowVersion)version;
                    toolVersion.setMetaVersion(String.valueOf(castedWorkflowVersion.getLastModified() != null ? castedWorkflowVersion.getLastModified() : new Date(0)));
                }
                final List<DescriptorType> descriptorType = toolVersion.getDescriptorType();
                if (!descriptorType.isEmpty()) {
                    EnumSet<DescriptorType> set = EnumSet.copyOf(descriptorType);
                    toolVersion.setDescriptorType(Lists.newArrayList(set));
                    // can assume in Dockstore that a single version only has one language
                    toolVersion.setDescriptorTypeVersion(Map.of(descriptorType.get(0).toString(), Lists.newArrayList(version.getVersionMetadata().getDescriptorTypeVersions())));
                }
                tool.getVersions().add(toolVersion);
            }
        }
        return tool;
    }

    /**
     * Get an openapi descriptor type from a descriptor language file type
     *
     * @param fileType a descriptor language file type such as FileType.DOCKSTORE_CWL
     * @return an Optional openapi descriptor type such as GALAXY("GALAXY") or SMK("SMK");
     */
    public static Optional<DescriptorType> getDescriptorTypeFromFileType(FileType fileType) {
        DescriptorLanguage descriptorLanguage;
        try {
            descriptorLanguage = DescriptorLanguage.getDescriptorLanguage(fileType);
        } catch (UnsupportedOperationException exception) {
            return Optional.empty();
        }

        return getDescriptorTypeFromDescriptorLanguage(descriptorLanguage);
    }

    /**
     * Get an openapi descriptor type from a descriptor language
     *
     * @param descriptorLanguage a descriptor language such as DescriptorLanguage.GXFORMAT2 or DescriptorLanguage.SMK
     * @return an Optional openapi descriptor type such as GALAXY("GALAXY") or SMK("SMK");
     */
    public static Optional<DescriptorType> getDescriptorTypeFromDescriptorLanguage(DescriptorLanguage descriptorLanguage) {
        // Tricky case for GALAXY because it doesn't match the rules of the other languages
        if (descriptorLanguage == DescriptorLanguage.GXFORMAT2) {
            return Optional.of(DescriptorType.GALAXY);
        }
        String descriptorType = descriptorLanguage.getShortName();
        return Arrays.stream(DescriptorType.values())
            .filter(descType -> descriptorType.equalsIgnoreCase(descType.toString())).findFirst();
    }

    /**
     * Constructs the image_name for ImageData
     *
     * @param image
     * @return The image_name
     * @throws CustomWebApplicationException if the sha256 digest does not exist for a Docker image that's specified by digest
     */
    private static String constructImageName(final Image image) throws CustomWebApplicationException {
        DockerSpecifier specifier = image.getSpecifier();
        Registry registry = image.getImageRegistry();
        String fullRepositoryName = image.getRepository();

        if (registry != Registry.DOCKER_HUB) {
            // If the registry is not Docker Hub (ex: Quay), prepend the image registry docker path
            fullRepositoryName = String.join("/", image.getImageRegistry().getDockerPath(), fullRepositoryName);
        }

        // Check if specifier is null because tool images don't have the DockerSpecifier set properly yet. For now, if it's null, assume that it's a tag
        // TODO: Remove null check once the DockerSpecifier for tool images are set properly
        if (specifier == null || specifier == DockerSpecifier.TAG) {
            return String.join(":", fullRepositoryName, image.getTag());
        } else if (specifier == DockerSpecifier.DIGEST) {
            // The image's sha256 checksum is the image's digest
            String imageDigest = image.getChecksums().stream()
                    .filter(checksum -> checksum.getType().equals("sha256"))
                    .findFirst()
                    .orElseThrow(() -> new CustomWebApplicationException("Could not find sha256 digest for Docker image specified by digest",
                            HttpStatus.SC_BAD_REQUEST))
                    .toString();
            return String.join("@", fullRepositoryName, imageDigest);
        } else {
            // Shouldn't really get here because all saved images are specified by tag or digest
            LOG.error("DockerSpecifier should be TAG or DIGEST, not {}", specifier);
            return "";
        }
    }

    private static List<ImageData> processImageDataForWorkflowVersions(final Version<?> version) {
        Set<Image> images = version.getImages();
        List<ImageData> trsImages = new ArrayList<>();

        for (Image image : images) {
            ImageData imageData = new ImageData();
            imageData.setImageType(ImageType.DOCKER);
            if (image.getImageRegistry() == null) {
                // avoid exception on null image registry
                continue;
            }
            imageData.setRegistryHost(image.getImageRegistry().getDockerPath());
            imageData.setImageName(constructImageName(image));
            imageData.setUpdated(image.getImageUpdateDate());
            imageData.setSize(image.getSize());
            List<Checksum> trsChecksums = new ArrayList<>();
            List<io.dockstore.webservice.core.Checksum> checksumList = image.getChecksums();

            for (io.dockstore.webservice.core.Checksum checksum : checksumList) {
                Checksum trsChecksum = new Checksum();
                trsChecksum.setType(DOCKER_IMAGE_SHA_TYPE_FOR_TRS);
                trsChecksum.setChecksum(checksum.getChecksum());
                trsChecksums.add(trsChecksum);
            }
            imageData.setChecksum(trsChecksums);
            trsImages.add(imageData);
        }
        return trsImages;
    }

    /**
     * creates image data for tools (in the Dockstore definition specifically)
     * @param castedContainer Dockstore tool
     * @param version tag for Dockstore tool
     * @param toolVersion toolVersion to return in TRS
     */
    public static void processImageDataForToolVersion(io.dockstore.webservice.core.Tool castedContainer, Tag version,
        ToolVersion toolVersion) {
        List<Checksum> trsChecksums = new ArrayList<>();
        if (version.getImages() != null && !version.getImages().isEmpty()) {
            version.getImages().forEach(image -> {
                if (image.getImageRegistry() != null) {
                    // avoid exception on null image registry
                    ImageData data = new ImageData();
                    image.getChecksums().forEach(checksum -> {
                        Checksum trsChecksum = new Checksum();
                        trsChecksum.setType(DOCKER_IMAGE_SHA_TYPE_FOR_TRS);
                        trsChecksum.setChecksum(checksum.getChecksum());
                        trsChecksums.add(trsChecksum);
                    });
                    //TODO: for now, all container images are Docker based
                    data.setImageType(ImageType.DOCKER);
                    data.setSize(image.getSize());
                    data.setUpdated(image.getImageUpdateDate());
                    data.setRegistryHost(image.getImageRegistry().getDockerPath());
                    data.setImageName(constructImageName(image));
                    data.setChecksum(trsChecksums);
                    toolVersion.getImages().add(data);
                }
            });
        } else {
            toolVersion.getImages().add(createDummyImageData(castedContainer));
        }
    }

    /**
     * This is a workaround used when the version.getImages() has nothing in it
     * @param castedContainer
     * @return
     */
    private static ImageData createDummyImageData(io.dockstore.webservice.core.Tool castedContainer) {
        ImageData data = new ImageData();
        //TODO: for now, all container images are Docker based
        data.setImageType(ImageType.DOCKER);
        // Not a proper size because we don't have that information stored in version.getImages()
        data.setSize(0L);
        // Not a proper date because we don't have that information stored in version.getImages()
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        data.setUpdated(formatter.format(now));
        data.setImageName(constructName(Arrays.asList(castedContainer.getRegistry(), castedContainer.getNamespace(), castedContainer.getName())));
        data.setRegistryHost(castedContainer.getRegistry());
        data.setChecksum(Lists.newArrayList());
        return data;
    }

    /**
     * Whether to hide the ToolVersion in TRS or not
     * @param version   Dockstore version
     * @param showHiddenTags    Whether the user has read access to the Dockstore version or not
     * @return
     */
    private static boolean shouldHideToolVersion(Version<?> version, boolean showHiddenTags, boolean isHosted) {
        // Hide version if no name
        if (version.getName() == null) {
            return true;
        }
        // Hide hidden versions if not authenticated
        if (version.isHidden() && !showHiddenTags) {
            return true;
        }
        // Hide tags with no image ID (except hosted tools which do not have image IDs)
        return version instanceof Tag && (((Tag)version).getImageId() == null && version.getImages().isEmpty()) && !isHosted;
    }

    /**
     * Construct escaped ID and then the URL of the Tool
     *
     * @param newID   The ID of the Tool
     * @param baseURL The base URL for the tools endpoint
     * @return The URL of the Tool
     * @throws UnsupportedEncodingException When URL encoding has failed
     */
    public static String getUrl(String newID, String baseURL) throws UnsupportedEncodingException {
        String escapedID = URLEncoder.encode(newID, StandardCharsets.UTF_8.displayName());
        return baseURL + escapedID;
    }

    /**
     * Get baseURL from DockstoreWebServiceConfiguration
     *
     * @param config The DockstoreWebServiceConfiguration
     * @return The baseURL for GA4GH tools endpoint (e.g. "http://localhost:8080/api/api/ga4gh/v2/tools/")
     * @throws URISyntaxException When URI building goes wrong
     */
    public static String baseURL(DockstoreWebserviceConfiguration config) throws URISyntaxException {
        int port = config.getExternalConfig().getPort() == null ? -1 : Integer.parseInt(config.getExternalConfig().getPort());
        // basePath should be "/" or "/api/"
        String basePath = MoreObjects.firstNonNull(config.getExternalConfig().getBasePath(), "/");
        // Example without the replace: "/api/" + "/api/ga4gh/v2" + "/tools/" = "/api//api/ga4gh/v2/tools"
        // Example with the replace: "/api/api/ga4gh/v2/tools"
        String baseURI = basePath + DockstoreWebserviceApplication.GA4GH_API_PATH_V2_FINAL.replaceFirst("/", "") + "/tools/";
        URI uri = new URI(config.getExternalConfig().getScheme(), null, config.getExternalConfig().getHostname(), port, baseURI, null, null);
        return uri.toString();
    }

    /**
     * Gets the checker workflow GA4GH path (test_tool_path) if it exists
     * @param config    The dockstore configuration file in order to find the base GA4GH path
     * @param entry     The entry to find its checker workflow path (test_tool_path)
     * @return          The checker workflow's GA4GH Tool ID
     */
    private static String getCheckerWorkflowPath(DockstoreWebserviceConfiguration config, Entry<?, ?> entry) {
        if (entry.getCheckerWorkflow() == null) {
            return null;
        }
        return getUrlFromId(config, getNewId(entry.getCheckerWorkflow()));
    }

    /**
     * Gets the new ID of the Tool
     *
     * @param entry The Dockstore Entry (Tool or Workflow)
     * @return The new ID of the Tool
     */
    private static String getNewId(Entry<?, ?> entry) {
        EntryTypeMetadata metadata = entry.getEntryTypeMetadata();
        if (metadata.isTrsSupported()) {
            return metadata.getTrsPrefix() + entry.getEntryPath();
        } else {
            LOG.error("TRS is not supported for {}, id: {}", metadata.getTermPlural(), entry.getId());
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
    private static ToolVersion setGeneralToolVersionInfo(String url, ToolVersion toolVersion, Version<?> version)
        throws UnsupportedEncodingException {
        String globalVersionId;
        globalVersionId = url + "/versions/" + URLEncoder.encode(version.getName(), StandardCharsets.UTF_8.displayName());
        toolVersion.setUrl(globalVersionId);
        toolVersion.setName(version.getName());
        toolVersion.setVerified(version.isVerified());
        String[] toolVerifiedSources = version.getVerifiedSources();
        toolVersion.setVerifiedSource(Lists.newArrayList(toolVerifiedSources));
        toolVersion.setContainerfile(false);
        return toolVersion;
    }

    /**
     * Set most of the GA4GH's Tool information that is not dependent on Dockstore's Tags or WorkflowVersions
     *
     * @param tool      The GA4GH Tool that will be modified
     * @param container The Dockstore Tool or Workflow
     * @return The modified Tool
     */
    private static Tool setGeneralToolInfo(Tool tool, Entry container) {
        // Set meta-version
        tool.setMetaVersion(container.getLastUpdated() != null ? container.getLastUpdated().toString() : new Date(0).toString());

        // Set type
        if (container instanceof io.dockstore.webservice.core.Tool || container instanceof AppTool) {
            tool.setToolclass(io.openapi.api.impl.ToolClassesApiServiceImpl.getCommandLineToolClass());
        } else if (container instanceof BioWorkflow) {
            tool.setToolclass(io.openapi.api.impl.ToolClassesApiServiceImpl.getWorkflowClass());
        } else if (container instanceof Service) {
            tool.setToolclass(io.openapi.api.impl.ToolClassesApiServiceImpl.getServiceClass());
        } else if (container instanceof Notebook) {
            tool.setToolclass(io.openapi.api.impl.ToolClassesApiServiceImpl.getNotebookClass());
        } else {
            throw new UnsupportedOperationException("encountered unknown entry type in TRS");
        }

        // Set description
        tool.setDescription(container.getDescription() != null ? container.getDescription() : "");

        return tool;
    }

    /**
     * Construct the workflow/tool full name
     *
     * @param strings The components that make up the full name (repository name + optional workflow/tool name)
     * @return The full workflow/tool name
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
     *
     * @param urlWithWorkDirectory
     * @param sourceFile The Dockstore SourceFile to convert
     * @return The resulting GA4GH ToolTests
     */
    public static FileWrapper sourceFileToToolTests(String urlWithWorkDirectory, SourceFile sourceFile, String selfPath) {
        DescriptorLanguage.FileType type = sourceFile.getType();
        if (!type.equals(DescriptorLanguage.FileType.WDL_TEST_JSON) && !type.equals(DescriptorLanguage.FileType.CWL_TEST_JSON) && !type.equals(
            DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS)) {
            LOG.error("This source file is not a recognized test file.");
        }
        ExtendedFileWrapper toolTests = new ExtendedFileWrapper();
        List<Checksum> trsChecksums = convertToTRSChecksums(sourceFile);
        toolTests.setChecksum(trsChecksums);
        toolTests.setUrl(urlWithWorkDirectory + sourceFile.getPath());
        toolTests.setContent(sourceFile.getContent());
        toolTests.setOriginalFile(sourceFile);
        toolTests.setDockstoreAbsolutePath(MoreObjects.firstNonNull(sourceFile.getAbsolutePath(), ""));
        toolTests.setDockstoreSelfUrl(selfPath);
        return toolTests;
    }

    private static List<Checksum> convertToTRSChecksums(final SourceFile sourceFile) {
        List<Checksum> trsChecksums = ToolsApiServiceImpl.convertToTRSChecksums(sourceFile);
        return trsChecksums;
    }

    /**
     * Create the GA4GH /tools/{id} url for a specific GA4GH Tool
     * @param config    The DockstoreWebserviceConfiguration which is used to get the baseURL
     * @param toolID    The ID of the GA4GH Tool
     * @return          The GA4GH /tools/{id} url
     */
    public static String getUrlFromId(DockstoreWebserviceConfiguration config, String toolID) {
        String url;
        if (toolID == null) {
            return null;
        } else {
            try {
                String baseURL = baseURL(config);
                url = getUrl(toolID, baseURL);
                return url;
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + toolID);
                return null;
            }
        }
    }
}
