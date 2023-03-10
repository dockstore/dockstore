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

import com.github.zafarkhaja.semver.UnexpectedCharacterException;
import com.github.zafarkhaja.semver.expr.LexerException;
import com.github.zafarkhaja.semver.expr.UnexpectedTokenException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.ExpressionTool;
import io.cwl.avro.Workflow;
import io.cwl.avro.WorkflowOutputParameter;
import io.cwl.avro.WorkflowStep;
import io.cwl.avro.WorkflowStepInput;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DockerImageReference;
import io.dockstore.common.LanguageHandlerHelper;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.SourceFileHelper;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * This class will eventually handle support for understanding CWL
 */
public class CWLHandler extends AbstractLanguageHandler implements LanguageHandlerInterface {
    public static final String CWL_VERSION_PREFIX = "v1";
    public static final Logger LOG = LoggerFactory.getLogger(CWLHandler.class);
    public static final String CWL_PARSE_ERROR = "Unable to parse CWL workflow, ";
    public static final String CWL_VERSION_ERROR = "CWL descriptor should contain a cwlVersion starting with " + CWLHandler.CWL_VERSION_PREFIX + ", detected version ";
    public static final String CWL_NO_VERSION_ERROR = "CWL descriptor should contain a cwlVersion";
    public static final String CWL_PARSE_SECONDARY_ERROR = "Syntax incorrect. Run command should specify a file name or process: ";
    public static final String METADATA_HINT_CLASS = "_dockstore_metadata";
    private static final String NODE_PREFIX = "dockstore_";
    private static final String TOOL_TYPE = "tool";
    private static final String WORKFLOW_TYPE = "workflow";
    private static final String EXPRESSION_TOOL_TYPE = "expressionTool";
    private static final String OPERATION_TYPE = "operation";
    private static final int CODE_SNIPPET_LENGTH = 50;
    private static final String COMMAND_LINE_TOOL = "CommandLineTool";
    private static final String WORKFLOW = "Workflow";
    private static final String EXPRESSION_TOOL = "ExpressionTool";
    private static final String OPERATION = "Operation";

    /**
     * A regular expression that matches all CWL version strings that we want to register as language versions.
     */
    private static final String LANGUAGE_VERSION_REGEX = "v1.[0-9]";
    private static final Pattern LANGUAGE_VERSION_PATTERN = Pattern.compile(LANGUAGE_VERSION_REGEX);

    @Override
    protected DescriptorLanguage.FileType getFileType() {
        return DescriptorLanguage.FileType.DOCKSTORE_CWL;
    }

    private String firstNonNullAndNonEmpty(String... values) {
        for (String value: values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Version parseWorkflowContent(String filePath, String content, Set<SourceFile> sourceFiles, Version version) {
        // parse the collab.cwl file to get important metadata
        if (content != null && !content.isEmpty()) {
            try {
                final Map<String, Object> map = parseSourceFiles(filePath, content, sourceFiles);

                // Extract various fields
                String description = null;
                try {
                    // draft-3 construct
                    description = (String)map.get("description");
                } catch (ClassCastException e) {
                    LOG.debug("\"description:\" is malformed, but was only in CWL draft-3 anyway");
                }
                String label = null;
                try {
                    label = (String)map.get("label");
                } catch (ClassCastException e) {
                    LOG.debug("\"label:\" is malformed");
                }
                // "doc:" added for CWL 1.0
                String doc = null;
                if (map.containsKey("doc")) {
                    Object objectDoc = map.get("doc");
                    if (objectDoc instanceof String) {
                        doc = (String)objectDoc;
                    } else if (objectDoc instanceof List) {
                        // arrays for "doc:" added in CWL 1.1
                        List docList = (List)objectDoc;
                        doc = String.join(System.getProperty("line.separator"), docList);
                    }
                }

                final String finalChoiceForDescription = firstNonNullAndNonEmpty(doc, description, label);

                if (finalChoiceForDescription != null) {
                    version.setDescriptionAndDescriptionSource(finalChoiceForDescription, DescriptionSource.DESCRIPTOR);
                } else {
                    LOG.info("Description not found!");
                }

                // Add authors from descriptor
                String dctKey = "dct:creator";
                String schemaKey = "s:author";
                if (map.containsKey(schemaKey)) {
                    processAuthor(version, map, schemaKey, "s:name", "s:email", "Author not found!");
                } else if (map.containsKey(dctKey)) {
                    processAuthor(version, map, dctKey, "foaf:name", "foaf:mbox", "Creator not found!");
                }

                setCwlVersionsFromSourceFiles(sourceFiles, version);

                LOG.info("Repository has Dockstore.cwl");
            } catch (YAMLException | JsonParseException | NullPointerException | ClassCastException ex) {
                String message;
                if (ex.getCause() != null) {
                    // seems to be possible to get underlying cause in some cases
                    message = ex.getCause().toString();
                } else {
                    // in other cases, the above will NullPointer
                    message = ex.toString();
                }
                LOG.info("CWL file is malformed " + message);
                // should just report on the malformed workflow
                Map<String, String> validationMessageObject = new HashMap<>();
                validationMessageObject.put(filePath, "CWL file is malformed or missing, cannot extract metadata: " + message);
                version.addOrUpdateValidation(new Validation(DescriptorLanguage.FileType.DOCKSTORE_CWL, false, validationMessageObject));
            }

        }
        return version;
    }

    Map<String, Object> parseSourceFiles(final String filePath, final String content,
        final Set<SourceFile> sourceFiles) {
        // Parse the file content
        Map<String, Object> map = parseAsMap(content);

        // Expand $import, $include, etc
        map = preprocess(map, filePath, new Preprocessor(sourceFiles));

        // Retarget to the main process, if necessary
        map = findMainProcess(map);
        return map;
    }

    /**
     * Examine the specified set of SourceFiles for CWL language versions,
     * set the language version of each SourceFile, and set the list of
     * language versions of the specified Version.
     *
     * In CWL, each SourceFile can contain multiple tools or subworkflows,
     * each with its own version.  If a SourceFile specifies multiple CWL
     * versions, the language version is set to the "latest" version.
     * The list of language versions for a given Version is sorted
     * in descending release date order, from "latest" to "earliest".
     */
    private void setCwlVersionsFromSourceFiles(Set<SourceFile> sourceFiles, Version version) {
        Set<String> allVersions = new HashSet<>();
        for (SourceFile file: sourceFiles) {
            if (file.getType() == DescriptorLanguage.FileType.DOCKSTORE_CWL) {
                Set<String> fileVersions = getCwlVersionsFromSourceFile(file);
                file.getMetadata().setTypeVersion(newestVersion(fileVersions));
                allVersions.addAll(fileVersions);
            }
        }
        version.getVersionMetadata().setDescriptorTypeVersions(sortVersionsDescending(allVersions));
    }

    /**
     * Extract the set of CWL language versions that appear in the
     * specified SourceFile.
     */
    private Set<String> getCwlVersionsFromSourceFile(SourceFile file) {
        Set<String> versions = new HashSet<>();
        getCwlVersionsFromMap(versions, parseAsMap(file.getContent()));
        return versions;
    }

    /**
     * Recursively examine the parsed YAML Map/List representation of
     * a CWL file, interpreting the string value of any Map entry with
     * the key "cwlVersion" as a language version if it matches the
     * pattern LANGUAGE_VERSION_PATTERN and ignoring it otherwise.
     */
    private void getCwlVersionsFromMap(Set<String> versions, Map<String, Object> map) {
        map.forEach((k, v) -> {
            if (Objects.equals(k, "cwlVersion") && v instanceof String version && LANGUAGE_VERSION_PATTERN.matcher(version).matches()) {
                versions.add(version);
            } else if (v instanceof Map) {
                getCwlVersionsFromMap(versions, (Map<String, Object>)v);
            }
        });
    }

    private String newestVersion(Set<String> versions) {
        return sortVersionsDescending(versions).stream().findFirst().orElse(null);
    }

    /**
     * Sort the specified set of versions in descending release date order, from
     * "latest" to "earliest".
     */
    private List<String> sortVersionsDescending(Set<String> versions) {
        return versions.stream().sorted(Comparator.comparing(this::convertVersionToSortKey).reversed()).collect(Collectors.toList());
    }

    /**
     * Create a sort key for the specified version string.
     * The version string is expected to be in the form 'vD.D', where D is a decimal number.
     * If it is not, a value representing release '0.0.0' is returned, allowing the sort to proceed.
     */
    private com.github.zafarkhaja.semver.Version convertVersionToSortKey(String version) {
        final com.github.zafarkhaja.semver.Version errorValue = com.github.zafarkhaja.semver.Version.valueOf("0.0.0");
        if (!version.startsWith("v")) {
            LOG.error("Version '{}' does not begin with 'v'", version);
            return errorValue;
        }
        final String release = version.substring(1) + ".0";
        try {
            return com.github.zafarkhaja.semver.Version.valueOf(release);
        } catch (IllegalArgumentException | UnexpectedCharacterException | LexerException | UnexpectedTokenException ex) {
            LOG.error("Exception parsing release number of version '{}'", version, ex);
            return errorValue;
        }
    }

    /**
     * Look at the map of metadata and populate entry with an author and email
     * @param version
     * @param map
     * @param dctKey
     * @param authorKey
     * @param emailKey
     * @param errorMessage
     */
    private void processAuthor(Version version, Map map, String dctKey, String authorKey, String emailKey, String errorMessage) {
        Object o = map.get(dctKey);
        if (o instanceof List) {
            o = ((List)o).get(0);
        }
        map = (Map)o;
        if (map != null) {
            String author = (String)map.get(authorKey);
            Author newAuthor = new Author(author);
            String email = (String)map.get(emailKey);
            if (!Strings.isNullOrEmpty(email)) {
                newAuthor.setEmail(email.replaceFirst("^mailto:", ""));
            }
            version.addAuthor(newAuthor);
        } else {
            LOG.info(errorMessage);
        }
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile) {
        Map<String, SourceFile> imports = new HashMap<>();
        processImport(repositoryId, content, version, sourceCodeRepoInterface, workingDirectoryForFile, imports);
        return imports;
    }

    private void processImport(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile, Map<String, SourceFile> imports) {

        try {
            Map<String, Object> fileContentMap = parseAsMap(content);
            handleMap(repositoryId, workingDirectoryForFile, version, imports, fileContentMap, sourceCodeRepoInterface);
        } catch (YAMLException | JsonParseException e) {
            SourceCodeRepoInterface.LOG.error("Could not process content from workflow as yaml", e);
        }
    }

    /**
     * Gets the file formats (either input or output) associated with the contents of a single CWL descriptor file
     * @param content   Contents of a CWL descriptor file
     * @param type      Either "inputs" or "outputs"
     * @return
     */
    public Set<FileFormat> getFileFormats(String content, String type) {
        Set<FileFormat> fileFormats = new HashSet<>();
        try {
            Map<String, Object> map = parseAsMap(content);
            Object targetType = map.get(type);
            if (targetType instanceof Map) {
                Map<String, ?> outputsMap = (Map<String, ?>)targetType;
                outputsMap.forEach((k, v) -> {
                    handlePotentialFormatEntry(fileFormats, v);
                });
            } else if (targetType instanceof List) {
                ((List)targetType).forEach(v -> {
                    handlePotentialFormatEntry(fileFormats, v);
                });
            } else {
                LOG.debug(type + " is not comprehensible.");
            }
        } catch (YAMLException | JsonParseException | NullPointerException e) {
            LOG.error("Could not process content from entry as yaml", e);
        }
        return fileFormats;
    }

    private void addFileFormat(Set<FileFormat> fileFormats, Object format) {
        if (format instanceof String) {
            FileFormat fileFormat = new FileFormat();
            fileFormat.setValue((String)format);
            fileFormats.add(fileFormat);
        } else {
            LOG.debug("malformed file format value");
        }
    }

    private void handlePotentialFormatEntry(Set<FileFormat> fileFormats, Object v) {
        if (v instanceof Map) {
            Map<String, Object> outputMap = (Map<String, Object>)v;
            Object format = outputMap.get("format");
            if (format instanceof List) {
                ((List<?>)format).forEach(formatElement -> addFileFormat(fileFormats, formatElement));
            } else {
                addFileFormat(fileFormats, format);
            }
        }
    }

    private Map<String, Object> preprocess(Map<String, Object> mapping, String mainDescriptorPath, Preprocessor preprocessor) {
        Object preprocessed = preprocessor.preprocess(mapping, mainDescriptorPath, null, 0);
        // If the preprocessed result is not a map, the CWL is not valid.
        if (!(preprocessed instanceof Map)) {
            String message = "CWL file is malformed";
            LOG.error(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
        return (Map<String, Object>)preprocessed;
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public Optional<String> getContent(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, LanguageHandlerInterface.Type type,
        ToolDAO dao) {
        try {
            // Initialize data structures for DAG
            Map<String, ToolInfo> toolInfoMap = new HashMap<>(); // Mapping of stepId -> array of dependencies for the step
            List<Pair<String, String>> nodePairs = new ArrayList<>();       // List of pairings of step id and dockerPull url
            Map<String, String> stepToType = new HashMap<>();               // Map of stepId -> type (expression tool, tool, workflow)

            // Initialize data structures for Tool table
            Map<String, DockerInfo> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url, docker specifier)

            // Convert CWL to object representation
            Map<String, Object> mapping = parseAsMap(mainDescriptor);

            // Expand "$import", "$include", "run:", etc
            Preprocessor preprocessor = new Preprocessor(secondarySourceFiles);
            mapping = preprocess(mapping, mainDescriptorPath, preprocessor);

            // Verify cwl version is correctly specified
            final Object cwlVersion = mapping.get("cwlVersion");
            if (cwlVersion != null) {
                final boolean startsWith = cwlVersion.toString().startsWith(CWLHandler.CWL_VERSION_PREFIX);
                if (!startsWith) {
                    LOG.error(CWLHandler.CWL_VERSION_ERROR + cwlVersion.toString());
                    throw new CustomWebApplicationException(CWLHandler.CWL_VERSION_ERROR
                        + cwlVersion.toString(), HttpStatus.SC_UNPROCESSABLE_ENTITY);
                }
            } else {
                LOG.error(CWLHandler.CWL_NO_VERSION_ERROR);
                throw new CustomWebApplicationException(CWLHandler.CWL_NO_VERSION_ERROR, HttpStatus.SC_UNPROCESSABLE_ENTITY);
            }

            // Retarget to the main process, if necessary.
            mapping = findMainProcess(mapping);

            // If the descriptor describes something other than a workflow, wrap and process it as a single-step workflow
            final Object cwlClass = mapping.get("class");
            if (!WORKFLOW.equals(cwlClass)) {
                mapping = convertToolToSingleStepWorkflow(mapping);
            }

            // Process the parse workflow
            Workflow workflow = parseWorkflow(mapping);
            processWorkflow(workflow, null, null, null, 0, type, dao, nodePairs, toolInfoMap, stepToType, nodeDockerInfo);

            // Return the requested information
            if (type == LanguageHandlerInterface.Type.DAG) {

                // Determine steps that point to end
                List<String> endDependencies = new ArrayList<>();

                if (workflow.getOutputs() != null) {
                    for (WorkflowOutputParameter outputParameter: workflow.getOutputs()) {
                        Object sources = outputParameter.getOutputSource();
                        processDependencies(endDependencies, sources, NODE_PREFIX);
                    }
                }

                toolInfoMap.put("UniqueEndKey", new ToolInfo(null, endDependencies));
                nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

                // connect start node with them
                for (Pair<String, String> node : nodePairs) {
                    if (toolInfoMap.get(node.getLeft()) == null) {
                        toolInfoMap.put(node.getLeft(), new ToolInfo(null, Lists.newArrayList("UniqueBeginKey")));
                    }
                }
                nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

                return Optional.of(setupJSONDAG(nodePairs, toolInfoMap, stepToType, nodeDockerInfo));
            } else {
                Map<String, DockerInfo> toolDockerInfo = nodeDockerInfo.entrySet().stream().filter(e -> "tool".equals(stepToType.get(e.getKey()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                return Optional.of(getJSONTableToolContent(toolDockerInfo));
            }
        } catch (ClassCastException | YAMLException | JsonParseException ex) {
            final String exMsg = CWL_PARSE_ERROR + ex.getMessage();
            LOG.error(exMsg, ex);
            throw new CustomWebApplicationException(exMsg, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    private Map<String, Object> convertToolToSingleStepWorkflow(Map<String, Object> tool) {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("cwlVersion", "v1.2");
        workflow.put("id", "_dockstore_wrapper");
        workflow.put("class", WORKFLOW);
        workflow.put("inputs", Map.of());
        workflow.put("outputs", Map.of());
        workflow.put("steps", Map.of("tool", Map.of("run", tool, "in", List.of(), "out", List.of())));
        return workflow;
    }

    /**
     * Convert the passed object to list representation.  CWL allows some lists of object to be
     * represented in either of two forms:
     * https://www.commonwl.org/v1.2/Workflow.html#map
     * If the passed object is in map form, it is converted to the corresponding list.
     * If the passed object is a list, it is returned unchanged.
     * @param listOrIdMap the object to be converted
     * @param idMapKey the key in the chld maps that each key in the parent map corresponds to
     * @param where a description of the location of this construct in the CWL file (ex: "in workflow step", "at line 13")
     */
    private List<Object> convertToList(Object listOrIdMap, String idMapKey, String where) {
        if (listOrIdMap instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>)listOrIdMap;
            List<Object> list = new ArrayList<>();
            map.forEach((key, value) -> {
                if (value instanceof Map) {
                    Map<Object, Object> valueMap = new LinkedHashMap<>((Map<Object, Object>)value);
                    valueMap.put(idMapKey, key);
                    list.add(valueMap);
                } else {
                    String message = CWL_PARSE_ERROR + "malformed cwl " + where;
                    LOG.error(message);
                    throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
                }
            });
            return list;
        } else if (listOrIdMap instanceof List) {
            return (List<Object>)listOrIdMap;
        } else if (listOrIdMap == null) {
            return List.of();
        } else {
            String message = CWL_PARSE_ERROR + "malformed cwl " + where;
            LOG.error(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Produce a new map in which the the hints and requirements in the given map are converted to list format.
     * @param map map to be converted
     * @param where where a description of the location of this construct in the CWL file (ex: "in workflow step", "at line 13")
     */
    private Map<Object, Object> convertRequirementsAndHintsToLists(Map<Object, Object> map, String where) {
        map = new LinkedHashMap<>(map);
        map.computeIfPresent("hints", (k, v) -> convertToList(v, "class", where));
        map.computeIfPresent("requirements", (k, v) -> convertToList(v, "class", where));
        return map;
    }

    /**
     * Parse a map to an instance of the specified class.
     * @param obj the object to parse
     * @param klass the class to parse to
     * @param description description of the object we are parsing, for error reporting purposes
     */
    private <T> T parseWithClass(Object obj, Class<T> klass, String description) {
        if (obj instanceof Map) {
            Gson gson = CWL.getTypeSafeCWLToolDocument();
            Map<Object, Object> map = convertRequirementsAndHintsToLists((Map<Object, Object>)obj, "in the requirements/hints of " + description);
            return gson.fromJson(gson.toJson(map), klass);
        } else {
            String message = CWL_PARSE_ERROR + "malformed cwl in " + description;
            LOG.error(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    private Workflow parseWorkflow(Object workflowObj) {
        return parseWithClass(workflowObj, Workflow.class, "workflow");
    }

    private CommandLineTool parseTool(Object toolObj) {
        return parseWithClass(toolObj, CommandLineTool.class, "tool");
    }

    private ExpressionTool parseExpressionTool(Object expressionToolObj) {
        return parseWithClass(expressionToolObj, ExpressionTool.class, "expression tool");
    }

    private WorkflowStep parseWorkflowStep(Object workflowStepObj) {
        return parseWithClass(workflowStepObj, WorkflowStep.class, "workflow step");
    }

    private boolean isProcessWithClass(Object candidate, String classValue) {
        return candidate instanceof Map && classValue.equals(((Map<Object, Object>)candidate).get("class"));
    }

    private boolean isWorkflow(Object candidate) {
        return isProcessWithClass(candidate, WORKFLOW);
    }

    private boolean isTool(Object candidate) {
        return isProcessWithClass(candidate, COMMAND_LINE_TOOL);
    }

    private boolean isExpressionTool(Object candidate) {
        return isProcessWithClass(candidate, EXPRESSION_TOOL);
    }

    private boolean isOperation(Object candidate) {
        return isProcessWithClass(candidate, OPERATION);
    }

    private String getWorkflowStepId(WorkflowStep workflowStep) {
        CharSequence id = workflowStep.getId();
        if (id == null) {
            String message = CWL_PARSE_ERROR + "missing id in workflow step";
            LOG.error(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
        return id.toString();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void processWorkflow(Workflow workflow, String parentStepId, RequirementOrHintState parentRequirementState, RequirementOrHintState parentHintState, int depth, LanguageHandlerInterface.Type type, ToolDAO dao, List<Pair<String, String>> nodePairs, Map<String, ToolInfo> toolInfoMap, Map<String, String> stepToType, Map<String, DockerInfo> nodeDockerInfo) {
        // Join parent and current requirements and hints.
        RequirementOrHintState requirementState = addToRequirementOrHintState(parentRequirementState, workflow.getRequirements());
        RequirementOrHintState hintState = addToRequirementOrHintState(parentHintState, workflow.getHints());

        // Iterate through steps to find dependencies and docker requirements.
        for (Object workflowStepObj: convertToList(workflow.getSteps(), "id", "in workflow steps")) {

            WorkflowStep workflowStep = parseWorkflowStep(workflowStepObj);

            String thisStepId = getWorkflowStepId(workflowStep);
            String fullStepId = parentStepId == null ? thisStepId : parentStepId + "." + thisStepId;
            String nodeStepId = NODE_PREFIX + fullStepId;

            if (depth == 0) {
                // Iterate over source and get the dependencies.
                ArrayList<String> stepDependencies = new ArrayList<>();
                if (workflowStep.getIn() != null) {
                    for (WorkflowStepInput stepInput: workflowStep.getIn()) {
                        Object sources = stepInput.getSource();
                        processDependencies(stepDependencies, sources, NODE_PREFIX);
                    }
                    if (stepDependencies.size() > 0) {
                        toolInfoMap.computeIfPresent(nodeStepId, (toolId, toolInfo) -> {
                            toolInfo.toolDependencyList.addAll(stepDependencies);
                            return toolInfo;
                        });
                        toolInfoMap.computeIfAbsent(nodeStepId, toolId -> new ToolInfo(null, stepDependencies));
                    }
                }
            }

            // Process any requirements and/or hints.
            RequirementOrHintState stepRequirementState = addToRequirementOrHintState(requirementState, workflowStep.getRequirements());
            RequirementOrHintState stepHintState = addToRequirementOrHintState(hintState, workflowStep.getHints());
            String stepDockerPath = getDockerPull(stepRequirementState, stepHintState);

            // Process the run object.
            Object runObj = workflowStep.getRun();
            String currentPath;

            if (isWorkflow(runObj)) {
                Workflow stepWorkflow = parseWorkflow(runObj);
                stepDockerPath = getDockerPull(stepRequirementState, stepWorkflow.getRequirements(), stepHintState, stepWorkflow.getHints());
                stepToType.put(nodeStepId, WORKFLOW_TYPE);
                currentPath = getDockstoreMetadataHintValue(stepWorkflow.getHints(), "path");
                // Process the subworkflow.
                processWorkflow(stepWorkflow, fullStepId, stepRequirementState, stepHintState, depth + 1, type, dao, nodePairs, toolInfoMap, stepToType, nodeDockerInfo);

            } else if (isTool(runObj)) {
                CommandLineTool tool = parseTool(runObj);
                stepDockerPath = getDockerPull(stepRequirementState, tool.getRequirements(), stepHintState, tool.getHints());
                stepToType.put(nodeStepId, TOOL_TYPE);
                currentPath = getDockstoreMetadataHintValue(tool.getHints(), "path");

            } else if (isExpressionTool(runObj)) {
                ExpressionTool expressionTool = parseExpressionTool(runObj);
                stepDockerPath = getDockerPull(stepRequirementState, expressionTool.getRequirements(), stepHintState, expressionTool.getHints());
                stepToType.put(nodeStepId, EXPRESSION_TOOL_TYPE);
                currentPath = getDockstoreMetadataHintValue(expressionTool.getHints(), "path");

            } else if (isOperation(runObj)) {
                stepToType.put(nodeStepId, OPERATION_TYPE);
                List<Object> operationHints = convertToList(((Map<Object, Object>)runObj).get("hints"), "class", "in Operation hints");
                currentPath = getDockstoreMetadataHintValue(operationHints, "path");

            } else if (runObj instanceof String) {
                // The run object is a String, which means it is a file name which the preprocessor could not expand.
                stepDockerPath = getDockerPull(stepRequirementState, stepHintState);
                stepToType.put(nodeStepId, "n/a");
                currentPath = runObj.toString();

            } else {
                LOG.error("Unexpected run object: " + runObj);
                String message = CWLHandler.CWL_PARSE_SECONDARY_ERROR + "in workflow step " + fullStepId;
                LOG.error(message);
                throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
            }

            if (currentPath == null) {
                currentPath = "";
            }

            // Extract some information from the docker pull, if it exists.
            DockerSpecifier dockerSpecifier = null;
            String dockerUrl = null;
            String stepType = stepToType.get(nodeStepId);
            if ((WORKFLOW_TYPE.equals(stepType) || TOOL_TYPE.equals(stepType)) && !Strings.isNullOrEmpty(stepDockerPath)) {
                // CWL doesn't support parameterized docker pulls. Must be a string.
                dockerSpecifier = LanguageHandlerInterface.determineImageSpecifier(stepDockerPath, DockerImageReference.LITERAL);
                dockerUrl = getURLFromEntry(stepDockerPath, dao, dockerSpecifier);
            }

            // Store the extracted information in the DAG/tool-list data structures.
            if (depth == 0 && type == LanguageHandlerInterface.Type.DAG) {
                nodePairs.add(new MutablePair<>(nodeStepId, dockerUrl));
            }
            nodeDockerInfo.put(nodeStepId, new DockerInfo(currentPath, stepDockerPath, dockerUrl, dockerSpecifier));
        }
    }

    /**
     * Read the value for a given key from the dockstore metadata hint, which was added by the preprocessor.
     */
    private String getDockstoreMetadataHintValue(List<Object> hints, String key) {
        if (hints == null) {
            return null;
        }
        Map<String, String> metadata = findMapInList(hints, "class", METADATA_HINT_CLASS);
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }

    private static Map findMapInList(List<Object> list, Object key, Object value) {
        return (Map)list.stream().filter(e -> e instanceof Map && value.equals(((Map)e).get(key))).findFirst().orElse(null);
    }

    private List<String> convertToSourceList(Object obj) {
        if (obj instanceof List) {
            return (List<String>)obj;
        } else if (obj instanceof String) {
            return List.of((String)obj);
        } else {
            String message = CWLHandler.CWL_PARSE_ERROR + "unexpected format of input/output list";
            LOG.error(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Computes the dependencies from one or more output sources
     * @param endDependencies list to which the computed dependencies are added
     * @param sourcesObj a single String output source or list of String output sources
     * @param nodePrefix prefix to attach to extracted dependencies
     */
    private void processDependencies(List<String> endDependencies, Object sourcesObj, String nodePrefix) {
        if (sourcesObj != null) {
            List<String> sources = convertToSourceList(sourcesObj);
            for (String s: sources) {
                // Split at the slashes, and if there are two or more parts, 
                // the dependency (workflow step name/id) is the second-to-last part.
                String[] split = s.split("/");
                if (split.length >= 2) {
                    endDependencies.add(nodePrefix + split[split.length - 2].replaceFirst("#", ""));
                }
            }
        }
    }

    /**
     * Iterates over a map of CWL file content looking for imports. When import is found. will grab the imported file from Git
     * and prepare it for import finding.
     * @param repositoryId              identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param parentFilePath            absolute path to the parent file which references the imported file
     * @param version                   version of the files to get
     * @param imports                   mapping of filenames to imports
     * @param fileContentMap            CWL file mapping
     * @param sourceCodeRepoInterface   used too retrieve imports
     */
    private void handleMap(String repositoryId, String parentFilePath, Version version, Map<String, SourceFile> imports, Map<String, ?> fileContentMap,
        SourceCodeRepoInterface sourceCodeRepoInterface) {
        Set<String> importKeywords = Sets.newHashSet("$import", "$include", "$mixin", "import", "include", "mixin");
        ParsedInformation parsedInformation = getParsedInformation(version, DescriptorLanguage.CWL);
        for (Map.Entry<String, ?> e : fileContentMap.entrySet()) {
            final Object mapValue = e.getValue();
            String absoluteImportPath;

            if (importKeywords.contains(e.getKey().toLowerCase())) {
                // handle imports and includes
                if (mapValue instanceof String) {
                    setImportsBasedOnMapValue(parsedInformation, (String)mapValue);
                    absoluteImportPath = unsafeConvertRelativePathToAbsolutePath(parentFilePath, (String)mapValue);
                    handleAndProcessImport(repositoryId, absoluteImportPath, version, imports, (String)mapValue, sourceCodeRepoInterface);
                }
            } else if (e.getKey().equalsIgnoreCase("run")) {
                // for workflows, bare files may be referenced. See https://github.com/dockstore/dockstore/issues/208
                //ex:
                //  run: {import: revtool.cwl}
                //  run: revtool.cwl
                if (mapValue instanceof String) {
                    setImportsBasedOnMapValue(parsedInformation, (String)mapValue);
                    absoluteImportPath = unsafeConvertRelativePathToAbsolutePath(parentFilePath, (String)mapValue);
                    handleAndProcessImport(repositoryId, absoluteImportPath, version, imports, (String)mapValue, sourceCodeRepoInterface);
                } else if (mapValue instanceof Map) {
                    // this handles the case where an import is used
                    handleMap(repositoryId, parentFilePath, version, imports, (Map)mapValue, sourceCodeRepoInterface);
                }
            } else {
                handleMapValue(repositoryId, parentFilePath, version, imports, mapValue, sourceCodeRepoInterface);
            }
        }
    }

    private void handleAndProcessImport(String repositoryId, String absolutePath, Version version, Map<String, SourceFile> imports, String relativePath, SourceCodeRepoInterface sourceCodeRepoInterface) {
        if (!imports.containsKey(absolutePath)) {
            handleImport(repositoryId, version, imports, relativePath, sourceCodeRepoInterface, absolutePath);
            SourceFile imported = imports.get(absolutePath);
            if (imported != null) {
                processImport(repositoryId, imported.getContent(), version, sourceCodeRepoInterface, absolutePath, imports);
            }
        }
    }

    /**
     * Sets the type of imports in ParsedInformation based on the import string
     * @param parsedInformation     A version's version metadata's
     * @param mapValue              Import string (should be either a local import or an HTTP(s) import
     */
    public static void setImportsBasedOnMapValue(ParsedInformation parsedInformation, String mapValue) {
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (urlValidator.isValid(mapValue)) {
            parsedInformation.setHasHTTPImports(true);
        } else {
            parsedInformation.setHasLocalImports(true);
        }
    }

    /**
     * Iterate over object and pass any mappings to check for imports.
     * @param repositoryId              identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param parentFilePath            absolute path to the parent file which references the imported file
     * @param version                   version of the files to get
     * @param imports                   mapping of filenames to imports
     * @param mapValue                  CWL file object
     * @param sourceCodeRepoInterface   used too retrieve imports
     */
    private void handleMapValue(String repositoryId, String parentFilePath, Version version, Map<String, SourceFile> imports,
        Object mapValue, SourceCodeRepoInterface sourceCodeRepoInterface) {
        if (mapValue instanceof Map) {
            handleMap(repositoryId, parentFilePath, version, imports, (Map)mapValue, sourceCodeRepoInterface);
        } else if (mapValue instanceof List) {
            for (Object listMember : (List)mapValue) {
                handleMapValue(repositoryId, parentFilePath, version, imports, listMember, sourceCodeRepoInterface);
            }
        }
    }

    /**
     * Determine dockerPull from requirement and hint state (requirements take precedence).
     *
     * @param requirementState
     * @param hintState
     * @return docker image name
     */
    private String getDockerPull(RequirementOrHintState requirementState, RequirementOrHintState hintState) {
        String dockerPull = requirementState.getDockerPull();
        if (dockerPull != null) {
            return dockerPull;
        }
        return hintState.getDockerPull();
    }

    private String getDockerPull(RequirementOrHintState requirementState, List<Object> additionalRequirements,
        RequirementOrHintState hintState, List<Object> additionalHints) {
        return getDockerPull(
            addToRequirementOrHintState(requirementState, additionalRequirements),
            addToRequirementOrHintState(hintState, additionalHints));
    }

    /**
     * Computes a new requirement/hint state by adding information-of-interest from the specified list of CWL requirements/hints.
     * If there are no requirements/hints to be added, the original state is returned.
     */
    private RequirementOrHintState addToRequirementOrHintState(RequirementOrHintState existing, List<Object> adds) {
        if (existing == null) {
            existing = new RequirementOrHintState();
        }
        if (adds == null || adds.isEmpty()) {
            return existing;
        }
        RequirementOrHintState sum = new RequirementOrHintState(existing);
        adds.forEach(add -> {
            if (add instanceof Map) {
                Map map = (Map)add;
                if ("DockerRequirement".equals(map.get("class"))) {
                    Object value = map.get("dockerPull");
                    if (value instanceof String) {
                        sum.setDockerPull((String)value);
                    }
                }
            }
        });
        return sum;
    }

    /**
     * Checks that the CWL file is the correct version
     * @param content
     * @return true if file is valid CWL version, false otherwise
     */
    private boolean isValidCwl(String content) {
        try {
            Map<String, Object> mapping = parseAsMap(content);
            final Object cwlVersion = mapping.get("cwlVersion");

            if (cwlVersion != null) {
                final boolean startsWith = cwlVersion.toString().startsWith(CWLHandler.CWL_VERSION_PREFIX);
                if (!startsWith) {
                    LOG.error("detected invalid version: " + cwlVersion.toString());
                }
                return startsWith;
            }
        } catch (ClassCastException | YAMLException | JsonParseException e) {
            return false;
        }
        return false;
    }

    private VersionTypeValidation validateProcessSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath,
        String processType, Set<String> processClasses, String oppositeType, Set<String> oppositeClasses) {

        List<DescriptorLanguage.FileType> fileTypes = new ArrayList<>(Collections.singletonList(DescriptorLanguage.FileType.DOCKSTORE_CWL));
        Set<SourceFile> filteredSourcefiles = filterSourcefiles(sourceFiles, fileTypes);
        Optional<SourceFile> mainDescriptor = filteredSourcefiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath))).findFirst();

        String validationMessage = null;

        if (mainDescriptor.isPresent()) {
            String content = mainDescriptor.get().getContent();
            if (StringUtils.isBlank(content)) {
                validationMessage = "Primary descriptor is empty.";
            } else {
                try {
                    Map<String, Object> parsed = findMainProcess(parseAsMap(content));
                    Object klass = parsed.get("class");
                    if (!processClasses.contains(klass)) {
                        validationMessage = String.format("A CWL %s requires %s.", processType, processClasses.stream().map(s -> String.format("'class: %s'", s)).collect(Collectors.joining(" or ")));
                        if (oppositeClasses.contains(klass)) {
                            validationMessage += String.format(" This file contains 'class: %s'. Did you mean to register a %s?", klass, oppositeType);
                        }
                    } else if (!this.isValidCwl(content)) {
                        validationMessage = "Invalid CWL version.";
                    }
                } catch (YAMLException | JsonParseException | ClassCastException e) {
                    LOG.error("An unsafe or malformed YAML was attempted to be parsed", e);
                    validationMessage = "CWL file is malformed or missing, cannot extract metadata: " + e.getMessage();
                }
            }
        } else {
            validationMessage = "Primary CWL descriptor is not present.";
        }

        if (validationMessage == null) {
            return new VersionTypeValidation(true, Collections.emptyMap());
        } else {
            return new VersionTypeValidation(false, Map.of(primaryDescriptorFilePath, validationMessage));
        }
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath, io.dockstore.webservice.core.Workflow workflow) {
        return validateProcessSet(sourceFiles, primaryDescriptorFilePath, "workflow", Set.of(WORKFLOW), "tool", Set.of(COMMAND_LINE_TOOL, EXPRESSION_TOOL));
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        return validateProcessSet(sourceFiles, primaryDescriptorFilePath, "tool", Set.of(COMMAND_LINE_TOOL, EXPRESSION_TOOL), "workflow", Set.of(WORKFLOW));
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return checkValidJsonAndYamlFiles(sourceFiles, DescriptorLanguage.FileType.CWL_TEST_JSON);
    }

    @Override
    public Optional<Boolean> isOpenData(final WorkflowVersion workflowVersion, final CheckUrlInterface checkUrlInterface) {
        final Optional<List<Input>> fileInputs = getFileInputs(workflowVersion);
        if (fileInputs.isEmpty()) { // Error reading primary descriptor/workflow
            return Optional.empty();
        }
        if (fileInputs.get().isEmpty()) { // There are no file inputs, consider it open
            return Optional.of(true);
        }
        final List<SourceFile> testFiles = workflowVersion.findTestFiles();
        if (testFiles.isEmpty()) {
            return Optional.of(false); // There are file inputs, but no test parameter files provided
        }
        return Optional.of(testFiles.stream().anyMatch(testFile -> {
            final Optional<JSONObject> jsonObject = SourceFileHelper.testFileAsJsonObject(testFile);
            if (jsonObject.isEmpty()) {
                return false;
            }
            return false;
        }));
    }

    /**
     * Returns an Optional list of the workflow version's file inputs. If the primary descriptor
     * does not exist or there is an error parsing the version's descriptors, returns an
     * <code>Optional.empty()</code>.
     * @param workflowVersion
     * @return
     */
    Optional<List<Input>> getFileInputs(WorkflowVersion workflowVersion) {
        final Optional<SourceFile> primaryDescriptor1 = workflowVersion.findPrimaryDescriptor();
        if (primaryDescriptor1.isEmpty()) {
            return Optional.empty();
        }
        final SourceFile sourceFile = primaryDescriptor1.get();
        try {
            final Map<String, Object> cwlMap =
                parseSourceFiles(workflowVersion.getWorkflowPath(), sourceFile.getContent(),
                    workflowVersion.getSourceFiles());
            return Optional.of(getInputs(cwlMap).stream()
                .filter(input -> input.type().contains("File")).toList());
        } catch (ClassCastException | YAMLException | JsonParseException ex) {
            LOG.error("Error determining file inputs", ex);
            return Optional.empty();
        }
    }

    private List<Input> getInputs(Map<String, Object> cwlDoc) {
        final Object inputs = cwlDoc.get("inputs");
        if (inputs instanceof Map inputsMap) {
            final Set<Map.Entry> set = inputsMap.entrySet();
            return set.stream().map(entry -> {
                final Object key = entry.getKey();
                if (key instanceof String keyString) {
                    final Object value = entry.getValue();
                    if (value instanceof Map valueMap) {
                        System.out.println("valueMap = " + valueMap);
                        Object type = valueMap.get("type");
                        Object secondaryFiles = valueMap.get("secondaryFiles");
                        if (type instanceof String typeString) {
                            return new Input(keyString, typeString);
                        }
                    }
                }
                return null;
            }).filter(Objects::nonNull).toList();
        }
        return List.of();
    }

    private Map<String, Object> findMainProcess(Map<String, Object> mapping) {

        // If the CWL is packed using the "$graph" syntax, the root is the process with id "#main":
        // https://www.commonwl.org/v1.2/Workflow.html#Packed_documents
        Object graph = mapping.get("$graph");
        if (graph instanceof List) {
            List<Object> processes = (List<Object>)graph;
            // Return the process with id "#main".
            for (Object process: processes) {
                if (process instanceof Map) {
                    Map<String, Object> processMapping = (Map<String, Object>) process;
                    if ("#main".equals(processMapping.get("id"))) {
                        return processMapping;
                    }
                }
            }
            // If there was no process with id "#main", return the first process as a fallback.
            // This isn't perfect, but it's a good guess, and better than nothing.
            if (!processes.isEmpty()) {
                Object process = processes.get(0);
                if (process instanceof Map) {
                    return (Map<String, Object>) process;
                }
            }
        }

        // Otherwise, assume this a normal CWL file.
        return mapping;
    }

    private static boolean isJsonObject(String yamlOrJson) {
        String trimmed = yamlOrJson.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static Object parse(String yamlOrJson) {
        if (isJsonObject(yamlOrJson)) {
            return new Gson().fromJson(yamlOrJson, Map.class);
        } else {
            new Yaml(new SafeConstructor()).load(yamlOrJson);
            return new Yaml().load(yamlOrJson);
        }
    }

    private static Map<String, Object> parseAsMap(String yamlOrJson) {
        Object parsed = parse(yamlOrJson);
        if (!(parsed instanceof Map)) {
            throw new YAMLException("Unexpected construct: " + StringUtils.abbreviate(yamlOrJson, CODE_SNIPPET_LENGTH));
        }
        return (Map<String, Object>)parsed;
    }

    static class RequirementOrHintState {

        private String dockerPull;

        RequirementOrHintState() {
        }

        RequirementOrHintState(RequirementOrHintState src) {
            setDockerPull(src.getDockerPull());
        }

        public void setDockerPull(String dockerPull) {
            this.dockerPull = dockerPull;
        }

        public String getDockerPull() {
            return dockerPull;
        }
    }

    /**
     * Implements a preprocessor which "expands" a CWL, replacing $import, $include, $mixin, and "run" directives per the CWL
     * spec https://www.commonwl.org/v1.2/Workflow.html, using the content of the referenced source files, with the exception
     * of a "run" directive that points to a missing source file, which is normalized to the "run: file" syntax and otherwise
     * left unchanged.
     *
     * <p>To facilitate the extraction of information from a CWL that is missing files, if an $import references a missing file,
     * it is replaced by the empty Map.  If an $include references a missing file, it is replaced by the empty string.
     *
     * <p>Typically, a Preprocessor instance is one-time-use: a new Preprocessor instance is created to expand each root CWL
     * descriptor.
     *
     * <p>As the preprocessor expands the CWL, for each process (workflow or tool) it encounters, it ensures that the process
     * has an id (by assigning a UUID if necessary), then adds the current file path to a special dockstore metadata hint.
     * This metadata hint is valid CWL and will propagate to a parsed representation, so we can later determine what file the
     * process came from.
     *
     * <p>During expansion, the preprocessor tracks three quantities to prevent denial-of-service attacks or infinite
     * loops due to a recursive CWL: the file import depth, the (approximate) total length of the expanded CWL in characters, and
     * total number of files expanded (incremented for each $import, $include, $mixin, and "run" directive).  If any of those
     * quantities exceed the maximum value, the preprocessor will call the handleMax function, the base implementation of which
     * will throw an exception.
     */
    public static class Preprocessor {
        private static final List<String> IMPORT_KEYS = Arrays.asList("$import", "import");
        private static final List<String> INCLUDE_KEYS = Arrays.asList("$include", "include");
        private static final List<String> MIXIN_KEYS = Arrays.asList("$mixin", "mixin");
        private static final int DEFAULT_MAX_DEPTH = 10;
        private static final long DEFAULT_MAX_CHAR_COUNT = 4L * 1024L * 1024L;
        private static final long DEFAULT_MAX_FILE_COUNT = 1000L;

        private final Set<SourceFile> sourceFiles;
        private long charCount;
        private long fileCount;
        private final int maxDepth;
        private final long maxCharCount;
        private final long maxFileCount;

        /**
         * Create a CWL Preprocessor with specified "max" values.  See the class javadoc regarding the meaning of the "max" arguments.
         * @param sourceFiles files to search when expanding $import, $include, etc
         * @param maxDepth the maximum file depth
         * @param maxCharCount the maximum number of expanded characters (approximate)
         * @param maxFileCount the maximum number of files expanded
         */
        public Preprocessor(Set<SourceFile> sourceFiles, int maxDepth, long maxCharCount, long maxFileCount) {
            this.sourceFiles = sourceFiles;
            this.charCount = 0;
            this.fileCount = 0;
            this.maxDepth = maxDepth;
            this.maxCharCount = maxCharCount;
            this.maxFileCount = maxFileCount;
        }

        /**
         * Create a CWL Preprocessor with default "max" values.
         */
        public Preprocessor(Set<SourceFile> sourceFiles) {
            this(sourceFiles, DEFAULT_MAX_DEPTH, DEFAULT_MAX_CHAR_COUNT, DEFAULT_MAX_FILE_COUNT);
        }

        /**
         * Preprocess the specified root-level CWL, recursively expanding various directives as noted in the class javadoc.
         * This method may, but does not necessarily, process the specified CWL in place.
         * @param cwl a representation of the CWL file content, typically a Map, List, or String and the result of new Yaml().load(content)
         * @param currentPath the path of the CWL file
         * @return the preprocessed CWL
         */
        public Object preprocess(Object cwl, String currentPath) {
            return preprocess(cwl, currentPath, null, 0);
        }

        /**
         * Preprocess the specified CWL, recursively expanding various directives as noted in the class javadoc.
         * This method may, but does not necessarily, process the specified CWL in place.
         * @param cwl a representation of the CWL file content or portion thereof, typically a Map, List, or String and the result of new Yaml().load(content)
         * @param currentPath the path of the CWL file
         * @param version the CWL version of the parent entry, null if there is no parent entry
         * @param depth the current file depth, where the root file is at depth 0, and the depth increases by one for each $import or $mixin
         * @return the preprocessed CWL
         */
        private Object preprocess(Object cwl, String currentPath, String version, int depth) {

            if (depth > maxDepth) {
                handleMax(String.format("maximum file depth (%d) exceeded", maxDepth));
            }

            if (cwl instanceof Map) {

                Map<String, Object> map = (Map<String, Object>)cwl;

                // If the map represents a workflow or tool, make sure it has an ID, record the path in the metadata, and determine the CWL version
                if (isProcess(map)) {
                    setIdIfAbsent(map);
                    setMetadataHint(map, Map.of("path", stripLeadingSlashes(currentPath)));
                    version = (String)map.getOrDefault("cwlVersion", version);
                }

                // Process $import, which is replaced by the parsed+preprocessed file content
                String importPath = findString(IMPORT_KEYS, map);
                if (importPath != null) {
                    return loadFileAndPreprocess(resolvePath(importPath, currentPath), emptyMap(), version, depth);
                }

                // Process $include, which is replaced by the literal string representation of the file content
                String includePath = findString(INCLUDE_KEYS, map);
                if (includePath != null) {
                    return loadFile(resolvePath(includePath, currentPath), "");
                }

                // Process $mixin, if supported by the current version
                // The referenced file content should parse and preprocess to a map
                // Then, for each (key,value) entry in the mixin map, (key,value) is added to the containing map if key does not already exist
                if (supportsMixin(version)) {
                    String mixinPath = findString(MIXIN_KEYS, map);
                    if (mixinPath != null) {
                        Object mixin = loadFileAndPreprocess(resolvePath(mixinPath, currentPath), emptyMap(), version, depth);
                        if (mixin instanceof Map) {
                            removeKey(MIXIN_KEYS, map);
                            applyMixin(map, (Map<String, Object>)mixin);
                        }
                    }
                }

                // Process each value of the Map
                preprocessMapValues(map, currentPath, version, depth);

            } else if (cwl instanceof List) {

                // Process each value of the List
                preprocessListValues((List<Object>)cwl, currentPath, version, depth);

            }

            return cwl;
        }

        private void preprocessMapValues(Map<String, Object> cwl, String currentPath, String version, int depth) {

            // Convert "run: {$import: <file>}" to "run: <file>"
            Object runValue = cwl.get("run");
            if (runValue instanceof Map) {
                String importValue = findString(IMPORT_KEYS, (Map<String, Object>)runValue);
                if (importValue != null) {
                    cwl.put("run", importValue);
                }
            }

            // Preprocess all values in the map
            cwl.replaceAll((k, v) -> preprocess(v, currentPath, version, depth));

            // Expand "run: <file>", leaving it unchanged if the file does not exist
            runValue = cwl.get("run");
            if (runValue instanceof String) {
                String runPath = (String)runValue;
                cwl.put("run", loadFileAndPreprocess(resolvePath(runPath, currentPath), runPath, version, depth));
            }
        }

        private void preprocessListValues(List<Object> cwl, String currentPath, String version, int depth) {
            cwl.replaceAll(v -> preprocess(v, currentPath, version, depth));
        }

        private boolean isProcess(Map<String, Object> cwl) {
            Object c = cwl.get("class");
            return WORKFLOW.equals(c) || COMMAND_LINE_TOOL.equals(c) || EXPRESSION_TOOL.equals(c) || OPERATION.equals(c);
        }

        private String setIdIfAbsent(Map<String, Object> entryCwl) {
            String currentId = (String)entryCwl.get("id");
            if (currentId == null) {
                entryCwl.put("id", java.util.UUID.randomUUID().toString());
            }
            return (String)entryCwl.get("id");
        }

        private void setMetadataHint(Map<String, Object> entryCwl, Map<String, String> entries) {
            // Create a hint Map that has the appropriate "class" and the desired entries
            Map<String, String> classedEntries = new HashMap<>(entries);
            classedEntries.put("class", METADATA_HINT_CLASS);
            // Find the hints object
            Object hints = entryCwl.get("hints");
            // If no hints, add an empty list
            if (hints == null) {
                hints = new ArrayList<Object>();
                entryCwl.put("hints", hints);
            }
            // Add the new metadata hint to the hints, replacing the existing metadata hint if it exists.
            // Hints can either be in List or "idmap" format, so handle both representations
            if (hints instanceof List) {
                List<Object> hintsList = (List<Object>)hints;
                hintsList.remove(findMapInList(hintsList, "class", METADATA_HINT_CLASS));
                hintsList.add(classedEntries);
            } else if (hints instanceof Map) {
                Map<String, Object> hintsMap = (Map<String, Object>)hints;
                hintsMap.put(METADATA_HINT_CLASS, classedEntries);
            }
        }

        private boolean supportsMixin(String version) {
            return version != null && version.startsWith("v1.0");
        }

        private String stripLeadingSlashes(String value) {
            return StringUtils.stripStart(value, "/");
        }

        private Map<String, Object> emptyMap() {
            return new LinkedHashMap<>();
        }

        private String findString(Collection<String> keys, Map<String, Object> map) {
            String key = findKey(keys, map);
            if (key == null) {
                return null;
            }
            Object value = map.get(key);
            if (value instanceof String) {
                return (String)value;
            }
            return null;
        }

        private void removeKey(Collection<String> keys, Map<String, Object> map) {
            String key = findKey(keys, map);
            if (key != null) {
                map.remove(key);
            }
        }

        private String findKey(Collection<String> keys, Map<String, Object> map) {
            for (String mapKey: map.keySet()) {
                if (keys.contains(mapKey.toLowerCase())) {
                    return (mapKey);
                }
            }
            return null;
        }

        private void applyMixin(Map<String, Object> to, Map<String, Object> mixin) {
            mixin.forEach(to::putIfAbsent);
        }

        private Object parse(String yamlOrJson) {
            return CWLHandler.parse(yamlOrJson);
        }

        private String resolvePath(String childPath, String parentPath) {
            if (childPath.startsWith("http://") || childPath.startsWith("https://")) {
                return null;
            }
            if (childPath.startsWith("file:")) {
                // The path in a file url is always absolute
                // See https://datatracker.ietf.org/doc/html/rfc8089
                childPath = childPath.replaceFirst("^file:/*+", "/");
            }
            return LanguageHandlerHelper.unsafeConvertRelativePathToAbsolutePath(parentPath, childPath);
        }

        private String loadFile(String loadPath, String notFoundValue) {
            fileCount++;
            if (fileCount > maxFileCount) {
                handleMax(String.format("maximum file count (%d) exceeded", maxFileCount));
            }

            for (SourceFile sourceFile: sourceFiles) {
                if (sourceFile.getAbsolutePath().equals(loadPath)) {
                    String content = sourceFile.getContent();
                    charCount += content.length();
                    if (charCount > maxCharCount) {
                        handleMax(String.format("maximum character count (%d) exceeded", maxCharCount));
                    }
                    return content;
                }
            }
            return notFoundValue;
        }

        private Object loadFileAndPreprocess(String loadPath, Object notFoundValue, String version, int depth) {
            String content = loadFile(loadPath, null);
            if (content == null) {
                return notFoundValue;
            }
            return preprocess(parse(content), loadPath, version, depth + 1);
        }

        /**
         * Invoked by the preprocessor when one of the "max" conditions (excessive file depth, size, or number of files expanded) is detected.
         * This method can be overridden to implement alternative behavior, such as returning instead of throwing, allowing preprocessing to continue.
         * @param message a message describing which "max" condition was met
         */
        public void handleMax(String message) {
            String fullMessage = "CWL might be recursive: " + message;
            LOG.error(fullMessage);
            throw new CustomWebApplicationException(fullMessage, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }
    public record Input(String name, String type) {}

}
