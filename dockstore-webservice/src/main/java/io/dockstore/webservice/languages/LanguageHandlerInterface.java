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
package io.dockstore.webservice.languages;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DockerImageReference;
import io.dockstore.common.LanguageHandlerHelper;
import io.dockstore.common.Registry;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.dockerhub.DockerHubImage;
import io.dockstore.webservice.core.dockerhub.DockerHubTag;
import io.dockstore.webservice.core.dockerhub.Results;
import io.dockstore.webservice.helpers.AbstractImageRegistry;
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.DAGHelper;
import io.dockstore.webservice.helpers.DockerRegistryAPIHelper;
import io.dockstore.webservice.helpers.QuayImageRegistry;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.model.QuayTag;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * This interface will be the future home of all methods that will need to be added to support a new workflow language
 */
public interface LanguageHandlerInterface {
    String DOCKERHUB_URL = AbstractImageRegistry.DOCKERHUB_URL;
    Logger LOG = LoggerFactory.getLogger(LanguageHandlerInterface.class);
    Gson GSON = new Gson();
    // public.ecr.aws/<registry_alias>/<repository_name>:<image_tag> -> public.ecr.aws/ubuntu/ubuntu:18.04
    // public.ecr.aws/<registry_alias>/<repository_name>@sha256:<image_digest>
    Pattern AMAZON_ECR_PUBLIC_IMAGE = Pattern.compile("(public\\.ecr\\.aws/)([a-z0-9._-]++)/([a-z0-9._/-]++)(:|@sha256:)(.++)");
    // <aws_account_id>.dkr.ecr.<region>.amazonaws.com/<repository_name>:<image_tag> -> 012345678912.dkr.ecr.us-east-1.amazonaws.com/test-repo:1
    // <aws_account_id>.dkr.ecr.<region>.amazonaws.com/<repository_name>@sha256:<image_digest>
    Pattern AMAZON_ECR_PRIVATE_IMAGE = Pattern.compile("([0-9]++)(\\.dkr\\.ecr\\.)([a-z0-9-]++)(\\.amazonaws.com/)([a-z0-9._/-]++)(:|@sha256:)(.++)");
    Pattern GOOGLE_PATTERN = Pattern.compile("((us|eu|asia)(.))?(gcr\\.io)(.+)");
    // <org>/<repository>:<version> -> broadinstitute/gatk:4.0.1.1
    // <org>/<repository>@sha256:<digest> -> broadinstitute/gatk@sha256:98b2f223dce4282c144d249e7e1f47d400ae349404409d01e87df2efeebac439
    Pattern DOCKER_HUB = Pattern.compile("(\\w)+/(.*)(:|@sha256:)(.+)");
    // <repo>:<version> -> postgres:9.6 Official Docker Hub images belong to the org "library", but that's not included when pulling the image
    // <repo>@256:<digest> -> ubuntu@sha256:d7bb0589725587f2f67d0340edb81fd1fcba6c5f38166639cf2a252c939aa30c
    Pattern OFFICIAL_DOCKER_HUB_IMAGE = Pattern.compile("(\\w|-)+(:|@sha256:)(.++)");
    // ghcr.io/<owner>/<image_name>:<image_tag> -> ghcr.io/icgc-argo/workflow-gateway
    // ghcr.io/<owner>/<image_name>@sha256:<image_digest>
    Pattern GITHUB_CONTAINER_REGISTRY_IMAGE = Pattern.compile("(ghcr\\.io)/([a-zA-Z0-9-]++)/([a-z0-9._/-]++)(:|@sha256:)(.++)");
    Pattern IMAGE_TAG_PATTERN = Pattern.compile("([^:]++):(\\S++)");
    Pattern IMAGE_DIGEST_PATTERN = Pattern.compile("([^@]++)@(\\S++)");

    /**
     * Parses the content of the primary descriptor to get author, email, and description
     *
     * @param filepath path to file
     * @param content a descriptor language document
     * @param sourceFiles
     * @param version the version to modify
     * @return
     */
    Version parseWorkflowContent(String filepath, String content, Set<SourceFile> sourceFiles, Version version);

    /**
     * Validates a workflow set for the workflow described by with primaryDescriptorFilePath
     * @param sourcefiles Set of sourcefiles
     * @param primaryDescriptorFilePath Primary descriptor path
     * @return Is a valid workflow set, error message
     */
    VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath, Workflow workflow);

    /**
     * Validates a tool set for the workflow described by with primaryDescriptorFilePath
     * @param sourcefiles Set of sourcefiles
     * @param primaryDescriptorFilePath Primary descriptor path
     * @return Is a valid tool set, error message
     */
    VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath);

    /**
     * Validates a test parameter set
     * @param sourceFiles Set of sourcefiles
     * @return Are all test parameter files valid, collection of error messages
     */
    VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles);

    /**
     * Parse a descriptor file and return a recursive mapping of its imports
     *
     * @param repositoryId            identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param content                 content of the primary descriptor
     * @param version                 version of the files to get
     * @param sourceCodeRepoInterface used to retrieve imports
     * @param filepath                used to help find relative imports, must be absolute
     * @return map of file paths to SourceFile objects
     */
    Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String filepath);

    /**
     * Read and process user-specified files.
     * @param repositoryId            identifies the git repository that we wish to use, normally something like 'organization/repo_name'
     * @param paths                   paths of the user-specified files
     * @param version                 version of the files to get
     * @param sourceCodeRepoInterface used to retrieve files
     * @param excludePaths            paths to exclude
     * @return map of file paths to user-specified SourceFile objects
     */
    default Map<String, SourceFile> processUserFiles(String repositoryId, List<String> paths, Version version, SourceCodeRepoInterface sourceCodeRepoInterface, Set<String> excludePaths) {
        if (paths != null && !paths.isEmpty()) {
            LOG.error("This language does not support user-specified files");
            throw new CustomWebApplicationException("This language does not support user-specified files", HttpStatus.SC_BAD_REQUEST);
        }
        return Map.of();
    }

    /**
     * Processes a descriptor and its associated secondary descriptors to either return the tools that a workflow has or a DAG representation
     * of a workflow
     *
     * @param mainDescriptorPath   the path of the main descriptor
     * @param mainDescriptor       the content of the main descriptor
     * @param secondarySourceFiles the content of the secondary descriptors in a map, looks like file paths -> content
     * @param type                 tools or DAG
     * @param dao                  used to retrieve information on tools
     * @return either a DAG or some form of a list of tools for a workflow
     */
    Optional<String> getContent(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, Type type, ToolDAO dao);

    /**
     * Checks that the test parameter files are valid JSON or YAML
     * Note: If even one is invalid, return invalid. Also merges all validation messages into one.
     * @param sourcefiles Set of sourcefiles
     * @param fileType Test parameter file type
     * @return Pair of isValid and validationMessage
     */
    default VersionTypeValidation checkValidJsonAndYamlFiles(Set<SourceFile> sourcefiles, DescriptorLanguage.FileType fileType) {
        boolean isValid = true;
        Map<String, String> validationMessageObject = new HashMap<>();
        for (SourceFile sourcefile : sourcefiles) {
            if (Objects.equals(sourcefile.getType(), fileType)) {
                Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
                try {
                    yaml.load(sourcefile.getContent());
                } catch (YAMLException e) {
                    LOG.error("There was an exception validating sourcefile", e);
                    validationMessageObject.put(sourcefile.getPath(), e.getMessage());
                    isValid = false;
                }
            }
        }
        return new VersionTypeValidation(isValid, validationMessageObject);
    }

    default String getCleanDAG(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, Type type, ToolDAO dao) {
        Optional<String> content = getContent(mainDescriptorPath, mainDescriptor, secondarySourceFiles, type, dao);
        return content.map(DAGHelper::cleanDAG).orElse(null);
    }

    default ParsedInformation getParsedInformation(Version version, DescriptorLanguage descriptorLanguage) {
        Optional<ParsedInformation> foundParsedInformation = version.getVersionMetadata().getParsedInformationSet().stream()
                .filter(parsedInformation -> parsedInformation.getDescriptorLanguage() == descriptorLanguage).findFirst();
        if (foundParsedInformation.isPresent()) {
            return foundParsedInformation.get();
        } else {
            ParsedInformation parsedInformation = new ParsedInformation();
            parsedInformation.setDescriptorLanguage(descriptorLanguage);
            version.getVersionMetadata().getParsedInformationSet().add(parsedInformation);
            return parsedInformation;
        }
    }

    /**
     * Removes any sourcefiles of some file types from a set
     * @param sourcefiles
     * @param fileTypes
     * @return Filtered sourcefile set
     */
    default Set<SourceFile> filterSourcefiles(Set<SourceFile> sourcefiles, List<DescriptorLanguage.FileType> fileTypes) {
        return sourcefiles.stream()
                .filter(sourcefile -> fileTypes.contains(sourcefile.getType()))
                .collect(Collectors.toSet());
    }

    /**
     * This method will setup the nodes (nodePairs) and edges (stepToDependencies) into Cytoscape compatible JSON
     *
     * @param nodePairs          looks like a list of node ids and docker pull information (often null)
     * @param stepToDependencies looks like a map of node ids to their parents
     * @param stepToType         looks like a list of node ids paired with their type
     * @param nodeDockerInfo     also looks like a list of node ids mapped to a triple describing where it came from and some docker information?
     * @return Cytoscape compatible JSON with nodes and edges
     */
    default String setupJSONDAG(List<Pair<String, String>> nodePairs, Map<String, ToolInfo> stepToDependencies,
            Map<String, String> stepToType, Map<String, DockerInfo> nodeDockerInfo) {
        List<Map<String, Map<String, String>>> nodes = new ArrayList<>();
        List<Map<String, Map<String, String>>> edges = new ArrayList<>();

        // Iterate over steps, make nodes and edges
        for (Pair<String, String> node : nodePairs) {
            String stepId = node.getLeft();
            String dockerUrl = null;
            if (nodeDockerInfo.get(stepId) != null) {
                dockerUrl = nodeDockerInfo.get(stepId).getDockerUrl();
            }

            Map<String, Map<String, String>> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", stepId);
            dataEntry.put("tool", dockerUrl);
            dataEntry.put("name", stepId.replaceFirst("^dockstore_", ""));
            dataEntry.put("type", stepToType.get(stepId));
            if (nodeDockerInfo.get(stepId) != null) {
                dataEntry.put("docker", nodeDockerInfo.get(stepId).getDockerImage());
                dataEntry.put("run", nodeDockerInfo.get(stepId).getRunPath());
            }
            nodeEntry.put("data", dataEntry);
            nodes.add(nodeEntry);

            // Make edges based on dependencies
            if (stepToDependencies.get(stepId) != null) {
                for (String dependency : stepToDependencies.get(stepId).toolDependencyList) {
                    Map<String, Map<String, String>> edgeEntry = new HashMap<>();
                    Map<String, String> sourceTarget = new HashMap<>();
                    sourceTarget.put("source", dependency);
                    sourceTarget.put("target", stepId);
                    edgeEntry.put("data", sourceTarget);
                    edges.add(edgeEntry);
                }
            }
        }

        Map<String, List<Map<String, Map<String, String>>>> dagJson = new LinkedHashMap<>();
        dagJson.put("nodes", nodes);
        dagJson.put("edges", edges);

        return convertToJSONString(dagJson);
    }

    // the following are helper methods used by implementations of getContent, messy, but not sure where to put them for now

    /**
     * This method will setup the tools of a workflow
     * It will then call another method to transform it through Gson to a Json string
     *
     * @param nodeDockerInfo map of stepId -> (run path, docker pull, docker url)
     * @return string representation of json table tool content
     */
    default String getJSONTableToolContent(Map<String, DockerInfo> nodeDockerInfo) {
        // set up JSON for Table Tool Content for all workflow languages
        ArrayList<Object> tools = new ArrayList<>();

        //iterate through each step within workflow file
        for (Map.Entry<String, DockerInfo> entry : nodeDockerInfo.entrySet()) {
            String key = entry.getKey();
            DockerInfo value = entry.getValue();
            //get the idName and fileName
            String fileName = value.getRunPath();

            //get the docker requirement
            String dockerPullName = value.getDockerImage();
            String dockerLink = value.getDockerUrl();
            DockerSpecifier dockerSpecifier = value.getDockerSpecifier();

            //put everything into a map, then ArrayList
            Map<String, String> dataToolEntry = new LinkedHashMap<>();
            dataToolEntry.put("id", key.replaceFirst("^dockstore_", ""));
            dataToolEntry.put("file", fileName);
            dataToolEntry.put("docker", dockerPullName);
            dataToolEntry.put("link", dockerLink);

            if (dockerSpecifier != null) {
                dataToolEntry.put("specifier", dockerSpecifier.name());
            }

            // Only add if docker and link are present
            if (dockerLink != null && dockerPullName != null) {
                tools.add(dataToolEntry);
            }
        }

        //call the GSON to string transformer
        return convertToJSONString(tools);
    }

    /**
     * This method will transform object containing the tools/dag of a workflow to Json string
     *
     * @param content has the final content of task/tool/node
     * @return String
     */
    default String convertToJSONString(Object content) {
        //create json string and return
        Gson gson = new Gson();
        String json = gson.toJson(content);
        LOG.debug(json);

        return json;
    }

    /**
     * Takes in a list of strings ["image_1", "image_2",..., "image_n"] and returns a formatted string: "image_1, image_2, ... and image_n"
     *
     * @param images : A list of docker image names
     * @return
     */
    default String formatImageStrings(final List<String> images) {
        int lastIndex = images.size() - 1;
        if (images.size() == 1) {
            return images.get(lastIndex);
        } else {
            String imagesString = String.join(", ", images.subList(0, lastIndex));
            imagesString = String.join(" and ", imagesString, images.get(lastIndex));
            return imagesString;
        }
    }

    /**
     * Check that all images are specified using a digest or tag.
     *
     * @param versionName of the workflow that the snapshot is being requested for
     * @param toolsJSONTable
     * @throws CustomWebApplicationException if there is at least one image that is specified by 'latest' tag, no tag, or parameter.
     */
    default void checkSnapshotImages(final String versionName, final String toolsJSONTable) throws CustomWebApplicationException {
        List<Map<String, String>> dockerTools = (ArrayList<Map<String, String>>)GSON.fromJson(toolsJSONTable, ArrayList.class);

        // Eliminate duplicate docker strings
        Map<String, DockerSpecifier> dockerStrings = dockerTools.stream().collect(Collectors.toMap(
            dockerTool -> dockerTool.get("docker"), dockerTool -> DockerSpecifier.valueOf(dockerTool.get("specifier")), (x, y) -> x));

        // Find invalid images
        Map<String, DockerSpecifier> invalidSnapshotImages = dockerStrings.entrySet().stream()
                .filter(image -> image.getValue() == DockerSpecifier.PARAMETER || image.getValue() == DockerSpecifier.LATEST
                        || image.getValue() == DockerSpecifier.NO_TAG)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Create error message
        if (invalidSnapshotImages.size() > 0) {
            List<String> parameterImages = invalidSnapshotImages.entrySet().stream()
                    .filter(image -> image.getValue() == DockerSpecifier.PARAMETER)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            List<String> latestImages = invalidSnapshotImages.entrySet().stream()
                    .filter(image -> image.getValue() == DockerSpecifier.LATEST)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            List<String> noTagImages = invalidSnapshotImages.entrySet().stream()
                    .filter(image -> image.getValue() == DockerSpecifier.NO_TAG)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            StringBuilder errorMessage = new StringBuilder(String.format(
                    "Snapshot for workflow version %s failed because not all images are specified using a digest nor a valid tag.",
                    versionName));

            if (parameterImages.size() > 1) {
                errorMessage.append(String.format(" Images %s are parameters.", formatImageStrings(parameterImages)));
            } else if (parameterImages.size() == 1) {
                errorMessage.append(String.format(" Image %s is a parameter.", parameterImages.get(0)));
            }

            if (noTagImages.size() > 1) {
                errorMessage.append(String.format(" Images %s have no tag.", formatImageStrings(noTagImages)));
            } else if (noTagImages.size() == 1) {
                errorMessage.append(String.format(" Image %s has no tag.", noTagImages.get(0)));
            }

            if (latestImages.size() > 1) {
                errorMessage.append(String.format(" Images %s are using the 'latest' tag.", formatImageStrings(latestImages)));
            } else if (latestImages.size() == 1) {
                errorMessage.append(String.format(" Image %s is using the 'latest' tag.", latestImages.get(0)));
            }

            throw new CustomWebApplicationException(errorMessage.toString(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Given a docker entry (quay, dockerhub, amazon ecr, or github container registry), return a URL to the given entry
     *
     * @param dockerEntry     has the docker name
     * @param toolDAO
     * @param dockerSpecifier has the type of specifier used to refer to the docker image
     * @return URL
     */
    // TODO: Potentially add support for other registries and add message that the registry is unsupported
    default String getURLFromEntry(final String dockerEntry, final ToolDAO toolDAO, final DockerSpecifier dockerSpecifier) {
        // For now ignore tag, later on it may be more useful
        String quayIOPath = "https://quay.io/repository/";
        String dockerHubPathR = "https://hub.docker.com/r/"; // For type repo/subrepo:tag
        String dockerHubPathUnderscore = "https://hub.docker.com/_/"; // For type repo:tag
        String dockstorePath = "https://www.dockstore.org/containers/"; // Update to tools once UI is updated to use /tools instead of /containers
        String amazonECRPublicPath = "https://gallery.ecr.aws/"; // Amazon ECR Public Gallery
        String url;

        // Remove tag or digest if exists
        String dockerImage = getImageNameWithoutSpecifier(dockerEntry, dockerSpecifier);

        if (dockerImage.isEmpty()) {
            return null;
        }

        // Regex for determining registry requires a tag; add a fake "0" tag
        Optional<Registry> registry = determineImageRegistry(dockerImage + ":0");

        // TODO: How to deal with multiple entries of a tool? For now just grab the first
        // TODO: How do we check that the URL is valid? If not then the entry is likely a local docker build
        if (registry.isPresent() && registry.get().equals(Registry.QUAY_IO)) {
            List<Tool> byPath = toolDAO.findAllByPath(dockerImage, true);
            if (byPath == null || byPath.isEmpty()) {
                // when we cannot find a published tool on Dockstore, link to quay.io
                url = dockerImage.replaceFirst("quay\\.io/", quayIOPath);
            } else {
                // when we found a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerImage;
            }
        } else if (registry.isPresent() && registry.get().equals(Registry.AMAZON_ECR)) {
            List<Tool> publishedByPath = toolDAO.findAllByPath(dockerImage, true);
            if (publishedByPath == null || publishedByPath.isEmpty()) {
                // Regex for Amazon ECR image requires a tag or digest; add a fake "0" tag
                if (AMAZON_ECR_PUBLIC_IMAGE.matcher(dockerEntry + ":0").matches()) {
                    // When we cannot find a published tool on Dockstore, link to Amazon ECR Public Gallery if it's a public image
                    url = dockerImage.replaceFirst("public\\.ecr\\.aws/", amazonECRPublicPath);
                } else {
                    // Return the entry as the url if it's a private Amazon ECR image
                    url = "https://" + dockerImage;
                }
            } else {
                // When we find a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerImage;
            }
        } else if (registry.isPresent() && registry.get().equals(Registry.GITHUB_CONTAINER_REGISTRY)) {
            List<Tool> publishedByPath = toolDAO.findAllByPath(dockerImage, true);
            if (publishedByPath == null || publishedByPath.isEmpty()) {
                // when we cannot find a published tool on Dockstore, link to GitHub Container Registry
                url = "https://" + dockerImage; // The docker image path redirects to the GitHub Package page for the image
            } else {
                // When we find a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerImage;
            }
        } else if (registry.isEmpty() || !registry.get().equals(Registry.DOCKER_HUB)) {
            // if the registry is neither Quay, Docker Hub, Amazon ECR nor GitHub Container Registry, return the entry as the url
            url = "https://" + dockerImage;
        } else {  // DOCKER_HUB
            String[] parts = dockerImage.split("/");
            if (parts.length == 2) {
                // if the path looks like pancancer/pcawg-oxog-tools
                List<Tool> publishedByPath = toolDAO.findAllByPath("registry.hub.docker.com/" + dockerImage, true);
                if (publishedByPath == null || publishedByPath.isEmpty()) {
                    // when we cannot find a published tool on Dockstore, link to docker hub
                    url = dockerHubPathR + dockerImage;
                } else {
                    // when we found a published tool, link to the tool on Dockstore
                    url = dockstorePath + "registry.hub.docker.com/" + dockerImage;
                }
            } else {
                // if the path looks like debian:8 or debian
                url = dockerHubPathUnderscore + dockerImage;
            }

        }

        return url;
    }

    /**
     * Returns an image name without the specifier (tag or digest), if present.
     * @param image
     * @param specifier
     * @return image name without specifier
     */
    default String getImageNameWithoutSpecifier(final String image, final DockerSpecifier specifier) {
        String imageNameWithoutSpecifier = image;
        // Remove tag or digest if exists
        Matcher m;
        if (specifier == DockerSpecifier.DIGEST) {
            m = IMAGE_DIGEST_PATTERN.matcher(image);
        } else {
            m = IMAGE_TAG_PATTERN.matcher(image);
        }
        if (m.matches()) {
            imageNameWithoutSpecifier = m.group(1);
        }

        // A specific architecture image from a multi-arch image is referenced by digest, but it may also include the tag for the multi-arch image
        // Ex: ubuntu:18.04@sha256:c404618e908391e50953e1ead94fe05dbbddbf532bd5c89b935ef34a9ca130d3 is the linux/amd64 image for ubuntu:18.04
        // Check for tag and remove if necessary
        if (specifier == DockerSpecifier.DIGEST) {
            m = IMAGE_TAG_PATTERN.matcher(imageNameWithoutSpecifier);
            if (m.matches()) {
                imageNameWithoutSpecifier = m.group(1);
            }
        }

        return imageNameWithoutSpecifier;
    }

    static DockerSpecifier determineImageSpecifier(String image, DockerImageReference imageReference) {
        DockerSpecifier dockerSpecifier = null;
        String latestTag = "latest";
        String digestSpecifer = "@sha256:";

        if (imageReference == DockerImageReference.DYNAMIC) {
            dockerSpecifier = DockerSpecifier.PARAMETER;
        } else if (imageReference == DockerImageReference.LITERAL) {
            // Determine how the image is specified
            if (image.contains(digestSpecifer)) {
                dockerSpecifier = DockerSpecifier.DIGEST;
            } else if (image.contains(":")) {
                String tagName = image.split(":")[1];
                if (tagName.equals(latestTag)) {
                    dockerSpecifier = DockerSpecifier.LATEST;
                } else {
                    dockerSpecifier = DockerSpecifier.TAG;
                }
            } else {
                dockerSpecifier = DockerSpecifier.NO_TAG;
            }
        }
        return dockerSpecifier;
    }

    default Optional<Registry> determineImageRegistry(String image) {
        if (image.startsWith("quay.io/")) {
            return Optional.of(Registry.QUAY_IO);
        } else if (image.startsWith("images.sbgenomics")) {
            return Optional.of(Registry.SEVEN_BRIDGES);
        } else if (image.startsWith("registry.gitlab.com")) {
            return Optional.of(Registry.GITLAB);
        } else if (GOOGLE_PATTERN.matcher(image).matches()) {
            return Optional.empty();
        } else if (AMAZON_ECR_PUBLIC_IMAGE.matcher(image).matches() || AMAZON_ECR_PRIVATE_IMAGE.matcher(image).matches()) {
            return Optional.of(Registry.AMAZON_ECR);
        } else if ((DOCKER_HUB.matcher(image).matches() || OFFICIAL_DOCKER_HUB_IMAGE.matcher(image).matches())) {
            return Optional.of(Registry.DOCKER_HUB);
        } else if (GITHUB_CONTAINER_REGISTRY_IMAGE.matcher(image).matches()) {
            return Optional.of(Registry.GITHUB_CONTAINER_REGISTRY);
        } else {
            return Optional.empty();
        }
    }

    // TODO: Implement then gitlab, seven bridges, google if possible;
    default Set<Image> getImagesFromRegistry(String toolsJSONTable) {
        List<Map<String, String>> dockerTools = (ArrayList<Map<String, String>>)GSON.fromJson(toolsJSONTable, ArrayList.class);

        // Eliminate duplicate docker strings
        Map<String, DockerSpecifier> dockerStrings = dockerTools.stream().collect(Collectors.toMap(dockertool -> dockertool.get("docker"), dockertool -> DockerSpecifier.valueOf(dockertool.get("specifier")), (x, y) -> x));

        Set<Image> dockerImages = new HashSet<>();

        for (Map.Entry<String, DockerSpecifier> dockerString : dockerStrings.entrySet()) {
            String image = dockerString.getKey();
            DockerSpecifier imageSpecifier = dockerString.getValue();

            Optional<Registry> registry = determineImageRegistry(image);
            Registry registryFound = registry.isEmpty() ? null : registry.get();
            if (registryFound == null || registryFound == Registry.GITLAB) {
                continue;
            } else {
                String repoName = getRepositoryName(registryFound, image, imageSpecifier);
                String specifierName = getSpecifierName(image, imageSpecifier);
                if (repoName.isEmpty()) {
                    LOG.error("URL to image {} on {} incomplete", image, registryFound.getFriendlyName());
                    continue;
                }
                if (specifierName.isEmpty()) {
                    LOG.error("Could not find specifier for image {} on {}", image, registryFound.getFriendlyName());
                    continue;
                }

                Set<Image> images = new HashSet<>();
                if (registryFound == Registry.QUAY_IO) {
                    images = getImageResponseFromQuay(repoName, imageSpecifier, specifierName);
                } else if (registryFound == Registry.DOCKER_HUB) {
                    images = getImagesFromDockerHub(repoName, imageSpecifier, specifierName);
                } else if (registryFound == Registry.GITHUB_CONTAINER_REGISTRY) {
                    images = DockerRegistryAPIHelper.getImages(Registry.GITHUB_CONTAINER_REGISTRY, repoName, imageSpecifier, specifierName);
                } else if (registryFound == Registry.AMAZON_ECR && AMAZON_ECR_PUBLIC_IMAGE.matcher(image).matches()) {
                    images = DockerRegistryAPIHelper.getImages(Registry.AMAZON_ECR, repoName, imageSpecifier, specifierName);
                }

                if (images.isEmpty()) {
                    LOG.error("Could not get image {} from {}", image, registryFound.getFriendlyName());
                    continue;
                }
                dockerImages.addAll(images);
            }
        }
        return dockerImages;
    }

    /**
     * Gets an image's full repository name.
     * The repository name returned is the image's name without the registry docker path (if applicable, like Quay and GHCR) and the specifier.
     * @param registry
     * @param image
     * @param specifier
     * @return repository name of the image
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    default String getRepositoryName(final Registry registry, final String image, final DockerSpecifier specifier) {
        String repoName = "";
        boolean isOfficialDockerHubImage = false;
        int minNumOfNameComponents = 0;
        int numOfNameComponents = image.split("/").length;

        if (registry == Registry.QUAY_IO || registry == Registry.GITHUB_CONTAINER_REGISTRY || (registry == Registry.AMAZON_ECR && AMAZON_ECR_PUBLIC_IMAGE.matcher(image).matches())) {
            minNumOfNameComponents = 3; // <registry_docker_path>/<owner>/<repo> -> quay.io/collaboratory/dockstore-tool-bamstats
        } else if (registry == Registry.AMAZON_ECR && AMAZON_ECR_PRIVATE_IMAGE.matcher(image).matches()) {
            minNumOfNameComponents = 2; // <aws_account_id>.dkr.ecr.<region>.amazonaws.com/<repository_name>
        } else if (registry == Registry.DOCKER_HUB) {
            if (OFFICIAL_DOCKER_HUB_IMAGE.matcher(image).matches()) {
                minNumOfNameComponents = 1; // <repo> -> python
                isOfficialDockerHubImage = true;
            } else {
                minNumOfNameComponents = 2; // <owner>/<repo> -> collaboratory/dockstore-tool-bamstats
            }
        }

        if (numOfNameComponents >= minNumOfNameComponents) {
            repoName = getImageNameWithoutSpecifier(image, specifier);

            if (registry == Registry.DOCKER_HUB && isOfficialDockerHubImage) {
                repoName = "library/" + repoName;
            } else {
                // Remove registry docker path
                if (repoName.startsWith(registry.getDockerPath())) {
                    repoName = repoName.replaceFirst(registry.getDockerPath() + "/", "");
                }
            }
        }

        return repoName;
    }

    /**
     * Gets the name of an image's specifier (tag or digest).
     * @param image
     * @param specifier
     * @return specifier name
     */
    default String getSpecifierName(final String image, final DockerSpecifier specifier) {
        String specifierName = "";
        Matcher m;
        if (specifier == DockerSpecifier.DIGEST) {
            m = IMAGE_DIGEST_PATTERN.matcher(image);
        } else {
            m = IMAGE_TAG_PATTERN.matcher(image);
        }
        if (m.matches()) {
            specifierName = m.group(2);
        }
        return specifierName;
    }

    default Set<Image> getImagesFromDockerHub(final String repo, final DockerSpecifier specifierType, final String specifierName) {
        Set<Image> dockerHubImages = new HashSet<>();
        Map<String, String> errorMap = new HashMap<>();
        Optional<String> response;
        boolean versionFound = false;
        DockerHubTag dockerHubTag = new DockerHubTag();
        String repoUrl = DOCKERHUB_URL + "repositories/" + repo + "/tags";

        if (specifierType != DockerSpecifier.DIGEST) {
            repoUrl += "?name=" + specifierName;
        }

        do {
            try {
                URL url = new URL(repoUrl);
                response = Optional.of(IOUtils.toString(url, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                LOG.error("Unable to get DockerHub response for " + repo, ex);
                response = Optional.empty();
            }

            if (response.isPresent()) {

                final String json = response.get();
                errorMap = (Map<String, String>)GSON.fromJson(json, errorMap.getClass());
                if (errorMap.get("message") != null) {
                    LOG.error("Error response from DockerHub: " + errorMap.get("message"));
                    return dockerHubImages;
                }

                // DockerHub seems to give empty results if something is not found, other fields are marked as null
                dockerHubTag = GSON.fromJson(json, DockerHubTag.class);
                List<Results> results = Arrays.asList(dockerHubTag.getResults());
                if (results.isEmpty()) {
                    LOG.error("Could not find any results for " + repo);
                    break;
                }

                for (Results r : results) {
                    if (specifierType == DockerSpecifier.DIGEST) {
                        // Look through images and find the image with the specified digest
                        List<DockerHubImage> images = Arrays.asList(r.getImages());

                        // For every version, DockerHub can provide multiple images, one for each os/architecture
                        images.stream().forEach(dockerHubImage -> {
                            final String manifestDigest = dockerHubImage.getDigest();
                            // Must perform null check for manifestDigest because there are Docker Hub images where the digest is null
                            if (manifestDigest != null && manifestDigest.equals(specifierName)) {
                                String tagName = r.getName(); // Tag that's associated with the image specified by digest
                                Checksum checksum = new Checksum(manifestDigest.split(":")[0], manifestDigest.split(":")[1]);
                                List<Checksum> checksums = Collections.singletonList(checksum);
                                // Docker Hub appears to return null for all the "last_pushed" properties of their images.
                                // Using the result's "last_pushed" as a workaround
                                Image archImage = new Image(checksums, repo, tagName, r.getImageID(), Registry.DOCKER_HUB,
                                        dockerHubImage.getSize(), r.getLastUpdated());

                                String osInfo = formatImageInfo(dockerHubImage.getOs(), dockerHubImage.getOsVersion());
                                String archInfo = formatImageInfo(dockerHubImage.getArchitecture(), dockerHubImage.getVariant());
                                archImage.setOs(osInfo);
                                archImage.setArchitecture(archInfo);
                                archImage.setSpecifier(specifierType);

                                dockerHubImages.add(archImage);
                            }
                        });

                        if (!dockerHubImages.isEmpty()) {
                            versionFound = true;
                            break;
                        }
                    } else if (r.getName().equals(specifierName)) { // match tag
                        List<DockerHubImage> images = Arrays.asList(r.getImages());
                        // For every version, DockerHub can provide multiple images, one for each os/architecture
                        images.stream().forEach(dockerHubImage -> {
                            final String manifestDigest = dockerHubImage.getDigest();
                            Checksum checksum = new Checksum(manifestDigest.split(":")[0], manifestDigest.split(":")[1]);
                            List<Checksum> checksums = Collections.singletonList(checksum);
                            // Docker Hub appears to return null for all the "last_pushed" properties of their images.
                            // Using the result's "last_pushed" as a workaround
                            Image archImage = new Image(checksums, repo, specifierName, r.getImageID(), Registry.DOCKER_HUB,
                                    dockerHubImage.getSize(), r.getLastUpdated());

                            String osInfo = formatImageInfo(dockerHubImage.getOs(), dockerHubImage.getOsVersion());
                            String archInfo = formatImageInfo(dockerHubImage.getArchitecture(), dockerHubImage.getVariant());
                            archImage.setOs(osInfo);
                            archImage.setArchitecture(archInfo);
                            archImage.setSpecifier(specifierType);

                            dockerHubImages.add(archImage);
                        });
                        versionFound = true;
                        break;
                    }
                }
                if (!versionFound) {
                    repoUrl = dockerHubTag.getNext();
                }
            }
        } while (response.isPresent() && !versionFound && dockerHubTag.getNext() != null);

        if (!versionFound) {
            LOG.error("Unable to find image with {}: {} from Docker Hub in repo {}", specifierType.name(), specifierName, repo);
        }

        return dockerHubImages;
    }

    static String formatImageInfo(String type, String version) {
        String imageInfo = null;
        if (type != null) {
            imageInfo = type;
            if (version != null) {
                imageInfo = imageInfo + "/" + version;
            }
        }
        return imageInfo;
    }

    /**
     * Get set of images for an image specified by tag or digest. If the image is multi-arch, then there will be more than one image in the set.
     * @param repo
     * @param specifierType
     * @param specifierName
     * @return
     */
    default Set<Image> getImageResponseFromQuay(String repo, DockerSpecifier specifierType, String specifierName) {
        Set<Image> quayImages = new HashSet<>();
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry();
        try {
            Optional<QuayTag> maybeTag;
            if (specifierType == DockerSpecifier.DIGEST) {
                // Look through all tags and find one with matching digest
                List<QuayTag> tags = quayImageRegistry.getAllQuayTags(repo);
                maybeTag = tags.stream().filter(quayTag -> quayTag.getManifestDigest().equals(specifierName)).findFirst();
            } else {
                // Get specific QuayTag
                maybeTag = quayImageRegistry.getQuayTag(repo, specifierName);
            }

            if (maybeTag.isEmpty()) {
                LOG.error("Unable to find image with specifier {}: {} from Quay in repo {}", specifierType, specifierName, repo);
                return quayImages;
            }

            QuayTag tag = maybeTag.get();
            if (quayImageRegistry.isMultiArchImage(tag, repo)) {
                List<QuayTag> cleanedQuayTagList = List.of(); // Don't need to keep track of cleaned tags because we're only processing one tag, so use empty list
                quayImages = quayImageRegistry.handleMultiArchQuayTags(repo, tag, cleanedQuayTagList, specifierType);
            } else {
                Image quayImage = quayImageRegistry.getImageForTag(repo, tag, specifierType);
                quayImages.add(quayImage);
            }
        } catch (ApiException ex) {
            LOG.error("Could not read from " + repo, ex);
        }
        return quayImages;
    }

    /**
     * Resolves a relative path based on an absolute parent path
     * @param parentPath Absolute path to parent file
     * @param relativePath Relative path the parent file
     * @return Absolute version of relative path
     */
    default String unsafeConvertRelativePathToAbsolutePath(String parentPath, String relativePath) {
        return LanguageHandlerHelper.unsafeConvertRelativePathToAbsolutePath(parentPath, relativePath);
    }

    /**
     * Terrible refactor in progress.
     * This code is used by both WDL and Nextflow to deal with the maps that we create for them.
     *
     * @param mainDescName    the filename of the main descriptor, used in the DAG list to indicate which tasks live in which descriptors
     * @param type            are we handling DAG or tools listing
     * @param dao             data access to tools
     * @param callType        ?
     * @param toolType        labels nodes of the DAG
     * @param toolInfoMap     map from names of tools to their dependencies (processes that had to come before) and to actual Docker containers that are used
     * @param namespaceToPath ?
     * @return the actual JSON output of either a DAG or tool listing
     */
    default Optional<String> convertMapsToContent(final String mainDescName, final Type type, ToolDAO dao, final String callType,
        final String toolType, Map<String, ToolInfo> toolInfoMap, Map<String, String> namespaceToPath) {

        // Initialize data structures for DAG
        List<Pair<String, String>> nodePairs = new ArrayList<>();
        Map<String, String> callToType = new HashMap<>();

        // Initialize data structures for Tool table
        Map<String, DockerInfo> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

        // Create nodePairs, callToType, toolID, and toolDocker
        for (Map.Entry<String, ToolInfo> entry : toolInfoMap.entrySet()) {
            String callId = entry.getKey();
            String docker = entry.getValue().dockerContainer;
            DockerSpecifier dockerSpecifier = entry.getValue().dockerSpecifier;
            nodePairs.add(new MutablePair<>(callId, docker));
            if (Strings.isNullOrEmpty(docker)) {
                callToType.put(callId, callType);
            } else {
                callToType.put(callId, toolType);
            }
            String dockerUrl = null;
            if (!Strings.isNullOrEmpty(docker)) {
                dockerUrl = getURLFromEntry(docker, dao, dockerSpecifier);
            }

            // Determine if call is imported
            String[] callName = callId.replaceFirst("^dockstore_", "").split("\\.");

            if (callName.length > 1) {
                nodeDockerInfo.put(callId, new DockerInfo(namespaceToPath.get(callName[0]), docker, dockerUrl, dockerSpecifier));
            } else {
                nodeDockerInfo.put(callId, new DockerInfo(mainDescName, docker, dockerUrl, dockerSpecifier));
            }
        }

        // Determine start node edges
        for (Pair<String, String> node : nodePairs) {
            ToolInfo toolInfo = toolInfoMap.get(node.getLeft());
            if (toolInfo.toolDependencyList.size() == 0) {
                toolInfo.toolDependencyList.add("UniqueBeginKey");
            }
        }
        nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

        // Determine end node edges
        Set<String> internalNodes = new HashSet<>(); // Nodes that are not leaf nodes
        Set<String> leafNodes = new HashSet<>(); // Leaf nodes

        for (Map.Entry<String, ToolInfo> entry : toolInfoMap.entrySet()) {
            List<String> dependencies = entry.getValue().toolDependencyList;
            internalNodes.addAll(dependencies);
            leafNodes.add(entry.getKey());
        }

        // Find leaf nodes by removing internal nodes
        leafNodes.removeAll(internalNodes);

        List<String> endDependencies = new ArrayList<>(leafNodes);

        toolInfoMap.put("UniqueEndKey", new ToolInfo(null, endDependencies));
        nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

        // Create JSON for DAG/table
        if (type == Type.DAG) {
            return Optional.of(setupJSONDAG(nodePairs, toolInfoMap, callToType, nodeDockerInfo));
        } else if (type == Type.TOOLS) {
            return Optional.of(getJSONTableToolContent(nodeDockerInfo));
        }

        return Optional.empty();
    }

    /**
     * Indicates if the workflow version can be run without access controlled data. This includes
     * <ul>
     *     <li>A workflow with no input parameters</li>
     *     <li>A workflow with input parameters, but none of them are file parameters</li>
     *     <li>A workflow with file input parameters, and at least one test parameter file that has
     *     publicly accessible urls for every file input parameter</li>
     * </ul>
     * @param workflowVersion
     * @param checkUrlInterface
     * @return if unable to determine, Optional.empty(), otherwise an non-empty boolean
     */
    default Optional<Boolean> isOpenData(WorkflowVersion workflowVersion, final CheckUrlInterface checkUrlInterface) {
        return Optional.empty();
    }

    enum Type {
        DAG, TOOLS
    }

    enum DockerSpecifier {
        /**
         * The image is not a string literal
         */
        PARAMETER,
        /**
         * The image is a string literal, but doesn't specify a tag
         */
        NO_TAG,
        /**
         * The image is a string literal with the tag "latest"
         */
        LATEST,
        /**
         * The image has a tag that is not "latest" nor a digest
         */
        TAG,
        /**
         * The image tag is a digest
         */
        DIGEST
    }

    class ToolInfo {

        protected final DockerSpecifier dockerSpecifier;

        /**
         * Currently, the id of a docker container as used by docker pull.
         * Due to some confusion, this is used by nfl and wdl, but not cwl.
         */
        String dockerContainer;
        /**
         * A list if ids for tools, processes that had to come before
         */
        List<String> toolDependencyList;

        ToolInfo(String dockerContainer, List<String> toolDependencyList) {
            this(dockerContainer, toolDependencyList, null);
        }

        ToolInfo(final String dockerContainer, final List<String> toolDependencyList, final DockerSpecifier dockerSpecifier) {
            this.dockerContainer = dockerContainer;
            this.toolDependencyList = toolDependencyList;
            this.dockerSpecifier = dockerSpecifier;
        }
    }

    class DockerInfo {
        private final String runPath;
        private final String dockerImage;
        private final String dockerUrl;
        private final DockerSpecifier dockerSpecifier;

        public DockerInfo(final String runPath, final String dockerImage, final String dockerUrl) {
            this(runPath, dockerImage, dockerUrl, null);
        }

        public DockerInfo(final String runPath, final String dockerImage, final String dockerUrl, final DockerSpecifier dockerSpecifier) {
            this.runPath = runPath;
            this.dockerImage = dockerImage;
            this.dockerUrl = dockerUrl;
            this.dockerSpecifier = dockerSpecifier;
        }

        public String getRunPath() {
            return runPath;
        }

        public String getDockerImage() {
            return dockerImage;
        }

        public String getDockerUrl() {
            return dockerUrl;
        }

        public DockerSpecifier getDockerSpecifier() {
            return dockerSpecifier;
        }
    }

}
