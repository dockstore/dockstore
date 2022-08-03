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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3id.cwl.cwl1_2.CommandLineTool;
import org.w3id.cwl.cwl1_2.DockerRequirement;
import org.w3id.cwl.cwl1_2.ExpressionTool;
import org.w3id.cwl.cwl1_2.Workflow;
import org.w3id.cwl.cwl1_2.WorkflowOutputParameter;
import org.w3id.cwl.cwl1_2.WorkflowStep;
import org.w3id.cwl.cwl1_2.WorkflowStepInput;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import org.w3id.cwl.cwl1_2.utils.ValidationException;
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
    public static final String CWL_PARSE_SECONDARY_ERROR = "Syntax incorrect. Could not ($)import or ($)include secondary file for run command: ";
    private static final String NODE_PREFIX = "dockstore_";
    private static final String TOOL_TYPE = "tool";
    private static final String WORKFLOW_TYPE = "workflow";
    private static final String EXPRESSION_TOOL_TYPE = "expressionTool";


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
                Yaml safeYaml = new Yaml(new SafeConstructor());
                // This should throw an exception if there are unexpected blocks
                safeYaml.load(content);
                Yaml yaml = new Yaml();
                Map map = yaml.loadAs(content, Map.class);

                // Expand $import, $include, etc
                map = preprocess(map, filePath, new Preprocessor(sourceFiles));

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

                LOG.info("Repository has Dockstore.cwl");
            } catch (YAMLException | NullPointerException | ClassCastException ex) {
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

        Yaml yaml = new Yaml();
        try {
            Yaml safeYaml = new Yaml(new SafeConstructor());
            // This should throw an exception if there are unexpected blocks
            safeYaml.load(content);
            Map<String, ?> fileContentMap = yaml.loadAs(content, Map.class);
            handleMap(repositoryId, workingDirectoryForFile, version, imports, fileContentMap, sourceCodeRepoInterface);
        } catch (YAMLException e) {
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
        Yaml yaml = new Yaml();
        try {
            Yaml safeYaml = new Yaml(new SafeConstructor());
            // This should throw an exception if there are unexpected blocks
            safeYaml.load(content);
            Map<String, ?> map = yaml.loadAs(content, Map.class);
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
        } catch (YAMLException | NullPointerException e) {
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
        Yaml yaml = new Yaml();
        try {
            Yaml safeYaml = new Yaml(new SafeConstructor());
            // This should throw an exception if there are unexpected blocks
            safeYaml.load(mainDescriptor);

            // Initialize data structures for DAG
            Map<String, ToolInfo> toolInfoMap = new HashMap<>(); // Mapping of stepId -> array of dependencies for the step
            List<Pair<String, String>> nodePairs = new ArrayList<>();       // List of pairings of step id and dockerPull url
            Map<String, String> stepToType = new HashMap<>();               // Map of stepId -> type (expression tool, tool, workflow)

            // Initialize data structures for Tool table
            Map<String, DockerInfo> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url, docker specifier)

            // Convert YAML to object representation
            Map<String, Object> mapping = yaml.loadAs(mainDescriptor, Map.class);

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

            // If the descriptor describes a tool, wrap and process it as a single-step workflow
            final Object cwlClass = mapping.get("class");
            if ("CommandLineTool".equals(cwlClass) || "ExpressionTool".equals(cwlClass)) {
                mapping = convertToolToSingleStepWorkflow(mapping);
            }

            Object rootObject;
            try {
                rootObject = RootLoader.loadDocument(mapping, "");
            } catch (ValidationException e) {
                LOG.error("The workflow does not seem to conform to CWL specs.");
                LOG.error("validation exception " + e.getMessage(), e);
                return Optional.empty();
            }

            if (!(rootObject instanceof Workflow)) {
                LOG.error("Unsupported CWL class.");
                return Optional.empty();
            }

            Workflow workflow = (Workflow)rootObject;
            processWorkflow(workflow, null, null, 0, null, type, preprocessor, dao, nodePairs, toolInfoMap, stepToType, nodeDockerInfo);

            if (type == LanguageHandlerInterface.Type.DAG) {
                // Determine steps that point to end
                List<String> endDependencies = new ArrayList<>();

                for (Object outputParameterObj : workflow.getOutputs()) {
                    LOG.info("OUTPUTS " + outputParameterObj.getClass());
                    if (outputParameterObj instanceof WorkflowOutputParameter) {
                        WorkflowOutputParameter outputParameter = (WorkflowOutputParameter)outputParameterObj;
                        Object sources = outputParameter.getOutputSource();
                        LOG.info("SOURCES " + sources.toString());
                        processDependencies(NODE_PREFIX, endDependencies, sources);
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
        } catch (ClassCastException | YAMLException ex) {
            final String exMsg = CWLHandler.CWL_PARSE_ERROR + ex.getMessage();
            LOG.error(exMsg, ex);
            throw new CustomWebApplicationException(exMsg, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    private Map<String, Object> convertToolToSingleStepWorkflow(Map<String, Object> tool) {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("cwlVersion", "v1.2");
        workflow.put("class", "Workflow");
        workflow.put("inputs", Map.of());
        workflow.put("outputs", Map.of());
        workflow.put("steps", Map.of("tool", Map.of("run", tool, "in", List.of(), "out", List.of())));
        return workflow;
    }

    private String convertStepId(String cwljavaStepId) {
        List<String> parts = Arrays.asList(cwljavaStepId.split("/"));
        return NODE_PREFIX + IntStream.range(0, parts.size()).filter(i -> i % 2 == 0 && i > 0).mapToObj(parts::get).collect(Collectors.joining("."));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void processWorkflow(Workflow workflow, Map<String, Map> parentRequirements, Map<String, Map> parentHints, int depth, String parentStepId, LanguageHandlerInterface.Type type, Preprocessor preprocessor, ToolDAO dao, List<Pair<String, String>> nodePairs, Map<String, ToolInfo> toolInfoMap, Map<String, String> stepToType, Map<String, DockerInfo> nodeDockerInfo) {
        LOG.error("XXX processing workflow " + deOptionalize(workflow.getId()));

        // Join parent and current requirements and hints.
        Map<String, Map> requirements = joinRequirementsOrHints(parentRequirements, workflow.getRequirements());
        Map<String, Map> hints = joinRequirementsOrHints(parentHints, workflow.getHints());

        // Iterate through steps to find dependencies and docker requirements
        for (Object workflowStepObj: workflow.getSteps()) {
            WorkflowStep workflowStep = (WorkflowStep)workflowStepObj;
            String workflowStepId = convertStepId(deOptionalize(workflowStep.getId()));
            LOG.error("XXX processing step " + workflowStepId);

            if (depth == 0) {
                ArrayList<String> stepDependencies = new ArrayList<>();

                // Iterate over source and get the dependencies
                if (workflowStep.getIn() != null) {
                    for (Object stepInputObj : workflowStep.getIn()) {
                        LOG.info("INS " + stepInputObj.getClass());
                        if (stepInputObj instanceof WorkflowStepInput) {
                            WorkflowStepInput stepInput = (WorkflowStepInput)stepInputObj;
                            Object sources = stepInput.getSource();
                            LOG.info("SOURCES " + sources.toString());
                            processDependencies(NODE_PREFIX, stepDependencies, sources);
                        }
                    }
                    if (stepDependencies.size() > 0) {
                        toolInfoMap.computeIfPresent(workflowStepId, (toolId, toolInfo) -> {
                            toolInfo.toolDependencyList.addAll(stepDependencies);
                            return toolInfo;
                        });
                        toolInfoMap.computeIfAbsent(workflowStepId, toolId -> new ToolInfo(null, stepDependencies));
                    }
                }
            }

            // Check workflow step for docker requirement and hints
            Map<String, Map> stepRequirements = joinRequirementsOrHints(requirements, workflowStep.getRequirements());
            Map<String, Map> stepHints = joinRequirementsOrHints(hints, workflowStep.getHints());
            String stepDockerPath = getDockerPull(stepRequirements, stepHints);

            // Check for docker requirement within workflow step file
            Object run = workflowStep.getRun();

            String currentPath;

            // TODO improve code by using Process interface etc
            if (run instanceof Workflow) {
                Workflow stepWorkflow = (Workflow)run;
                stepDockerPath = getDockerPull(
                    joinRequirementsOrHints(stepRequirements, stepWorkflow.getRequirements()),
                    joinRequirementsOrHints(stepHints, stepWorkflow.getHints()));
                stepToType.put(workflowStepId, WORKFLOW_TYPE);
                currentPath = preprocessor.getPath(deOptionalize(stepWorkflow.getId()));
                // Process the subworkflow
                processWorkflow(stepWorkflow, stepRequirements, stepHints, depth + 1, workflowStepId, type, preprocessor, dao, nodePairs, toolInfoMap, stepToType, nodeDockerInfo);

            } else if (run instanceof CommandLineTool) {
                CommandLineTool clTool = (CommandLineTool)run;
                stepDockerPath = getDockerPull(
                    joinRequirementsOrHints(stepRequirements, clTool.getRequirements()),
                    joinRequirementsOrHints(stepHints, clTool.getHints()));
                stepToType.put(workflowStepId, TOOL_TYPE);
                currentPath = preprocessor.getPath(deOptionalize(clTool.getId()));

            } else if (run instanceof ExpressionTool) {
                ExpressionTool expressionTool = (ExpressionTool)run;
                stepDockerPath = getDockerPull(
                    joinRequirementsOrHints(stepRequirements, expressionTool.getRequirements()),
                    joinRequirementsOrHints(stepHints, expressionTool.getHints()));
                stepToType.put(workflowStepId, EXPRESSION_TOOL_TYPE);
                currentPath = preprocessor.getPath(deOptionalize(expressionTool.getId()));

            } else if (run instanceof String) {
                stepToType.put(workflowStepId, "n/a");
                currentPath = run.toString();

            } else {
                LOG.error(CWLHandler.CWL_PARSE_SECONDARY_ERROR + run);
                throw new CustomWebApplicationException(CWLHandler.CWL_PARSE_SECONDARY_ERROR + run, HttpStatus.SC_UNPROCESSABLE_ENTITY);
            }

            if (currentPath == null) {
                currentPath = "";
            }

            DockerSpecifier dockerSpecifier = null;
            String dockerUrl = null;
            String stepType = stepToType.get(workflowStepId);
            if ((stepType.equals(WORKFLOW_TYPE) || stepType.equals(TOOL_TYPE)) && !Strings.isNullOrEmpty(stepDockerPath)) {
                // CWL doesn't support parameterized docker pulls. Must be a string.
                dockerSpecifier = LanguageHandlerInterface.determineImageSpecifier(stepDockerPath, DockerImageReference.LITERAL);
                dockerUrl = getURLFromEntry(stepDockerPath, dao, dockerSpecifier);
            }

            if (depth == 0 && type == LanguageHandlerInterface.Type.DAG) {
                nodePairs.add(new MutablePair<>(workflowStepId, dockerUrl));
            }

            nodeDockerInfo.put(workflowStepId, new DockerInfo(currentPath, stepDockerPath, dockerUrl, dockerSpecifier));
        }
    }

    private String convertToString(Object object) {
        return object != null ? object.toString() : null;
    }

    private void processDependencies(String nodePrefix, List<String> endDependencies, Object sources) {
        if (sources != null) {
            if (sources instanceof String) {
                String[] sourceSplit = ((String)sources).split("/");
                if (sourceSplit.length > 2) {
                    String v = nodePrefix + sourceSplit[sourceSplit.length - 2].replaceFirst("#", "");
                    LOG.info("V " + v);
                    endDependencies.add(v);
                }
            } else {
                List<String> filteredDependencies = filterDependent((List<String>)sources, nodePrefix);
                endDependencies.addAll(filteredDependencies);
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
     * Will determine dockerPull from requirements and hints (requirements take precedence)
     *
     * @param requirements
     * @param hints
     * @return
     */
    private String getDockerPull(Map<String, Map> requirements, Map<String, Map> hints) {
        String requirementPull = getDockerPull(requirements);
        if (requirementPull != null) {
            return requirementPull;
        }
        return getDockerPull(hints);
    }

    /**
     * Return the DockerPull from the specified CWL requirements/hints, null if not present.
     */
    private String getDockerPull(Map<String, Map> requirementsOrHints) {
        Map requirementOrHint = requirementsOrHints.get("DockerRequirement");
        if (requirementOrHint != null) {
            Object dockerPull = requirementOrHint.get("dockerPull");
            if (dockerPull != null) {
                return dockerPull.toString();
            }
        }
        return null;
    }

    /**
     * Converts a JSON Object in CWL to JSON Array
     * @param keyName Name of key to convert (Ex. requirements, hints)
     * @param entryJson JSON representation of file
     * @return Updated JSON representation of file
     */
    private void convertJSONObjectToArray(String keyName, JSONObject entryJson) {
        if (entryJson.has(keyName)) {
            if (entryJson.get(keyName) instanceof JSONObject) {
                JSONArray reqArray = new JSONArray();
                JSONObject requirements = (JSONObject)entryJson.get(keyName);
                requirements.keySet().stream().forEach(key -> {
                    JSONObject newReqEntry = requirements.getJSONObject(key);
                    newReqEntry.put("class", key);
                    reqArray.put(newReqEntry);
                });
                entryJson.put(keyName, reqArray);
            }
        }
    }

    /*
    private void convertRequirementsAndHintsToArray(JSONObject entryJson) {

        convertJSONObjectToArray("hints", entryJson);
        convertJSONObjectToArray("requirements", entryJson);

        // for each step, convert the hints and requirements in each step and in the entry that the step runs.
        if (entryJson.has("steps")) {
            Object steps = entryJson.get("steps");
            List<Object> stepValues;
            if (steps instanceof JSONObject) {
                JSONObject stepsObject = (JSONObject)steps;
                stepValues = stepsObject.keySet().stream().map(stepsObject::get).collect(Collectors.toList());
            } else if (steps instanceof JSONArray) {
                stepValues = ((JSONArray)steps).toList();
            } else {
                stepValues = Collections.emptyList();
            }
            for (Object stepValue: stepValues) {
                if (stepValue instanceof JSONObject) {
                    JSONObject stepObject = (JSONObject)stepValue;
                    convertJSONObjectToArray("hints", stepObject);
                    convertJSONObjectToArray("requirements", stepObject);
                    if (stepObject.has("run")) {
                        Object runValue = stepObject.get("run");
                        if (runValue instanceof JSONObject) {
                            convertRequirementsAndHintsToArray((JSONObject)runValue);
                        }
                    }
                }
            }
        }
    }
    */

    private <T> T deOptionalize(Optional<T> optional) {
        if (optional == null) {
            return null;
        }
        return optional.orElse(null);
    }

    private Map convertToMap(DockerRequirement dockerRequirement) {
        Map map = new LinkedHashMap();
        map.put("class", "DockerRequirement");
        map.put("dockerPull", deOptionalize(dockerRequirement.getDockerPull()));
        return map;
    }

    /**
     * Adds the List of new CWL requirements/hints to the specified Map of CWL requirements/hints.
     * Requirements/hints from the List take precedence over those in the Map.
     * If there are no requirements/hints to be added, the original Map is returned.
     */
    private Map<String, Map> joinRequirementsOrHints(Map<String, Map> existing, Optional<List<Object>> optionalAdd) {
        if (existing == null) {
            existing = Map.of();
        }
        List<Object> add = deOptionalize(optionalAdd);
        if (add == null || add.isEmpty()) {
            return existing;
        }
        Map<String, Map> sum = new HashMap<>(existing);
        add.forEach(obj -> {
            if (obj instanceof DockerRequirement) {
                obj = convertToMap((DockerRequirement)obj);
            }
            if (obj instanceof Map) {
                Map map = (Map)obj;
                Object klass = map.get("class");
                if (klass instanceof String) { 
                    sum.put((String)klass, map);
                    LOG.error("joining requirement " + (String)klass);
                }
            }
        });
        return sum;
    }

    /**
     * Checks if a file is a workflow (CWL)
     *
     * @param content
     * @return true if workflow, false otherwise
     */
    private boolean isWorkflow(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = yaml.loadAs(content, Map.class);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "Workflow".equals(cwlClass);
            }
        }
        return false;
    }

    /**
     * Checks if a file is an expression tool (CWL)
     *
     * @param content
     * @return true if expression tool, false otherwise
     */
    private boolean isExpressionTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = yaml.loadAs(content, Map.class);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "ExpressionTool".equals(cwlClass);
            }
        }
        return false;
    }

    /**
     * Checks if a file is a tool (CWL)
     *
     * @param content
     * @return true if tool, false otherwise
     */
    private boolean isTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = yaml.loadAs(content, Map.class);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "CommandLineTool".equals(cwlClass);
            }
        }
        return false;
    }

    /**
     * Checks that the CWL file is the correct version
     * @param content
     * @param yaml
     * @return true if file is valid CWL version, false otherwise
     */
    private boolean isValidCwl(String content, Yaml yaml) {
        try {
            Map<String, Object> mapping = yaml.loadAs(content, Map.class);
            final Object cwlVersion = mapping.get("cwlVersion");

            if (cwlVersion != null) {
                final boolean startsWith = cwlVersion.toString().startsWith(CWLHandler.CWL_VERSION_PREFIX);
                if (!startsWith) {
                    LOG.error("detected invalid version: " + cwlVersion.toString());
                }
                return startsWith;
            }
        } catch (ClassCastException | YAMLException e) {
            return false;
        }
        return false;
    }

    /**
     * Given an array of sources, will look for dependencies in the source name
     *
     * @param sources
     * @return filtered list of dependent sources
     */
    private List<String> filterDependent(List<String> sources, String nodePrefix) {
        List<String> filteredArray = new ArrayList<>();

        for (String s : sources) {
            String[] split = s.split("/");
            if (split.length > 1) {
                filteredArray.add(nodePrefix + split[0].replaceFirst("#", ""));
            }
        }

        return filteredArray;
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        List<DescriptorLanguage.FileType> fileTypes = new ArrayList<>(Collections.singletonList(DescriptorLanguage.FileType.DOCKSTORE_CWL));
        Set<SourceFile> filteredSourcefiles = filterSourcefiles(sourcefiles, fileTypes);
        Optional<SourceFile> mainDescriptor = filteredSourcefiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath))).findFirst();

        boolean isValid = true;
        boolean safe = false;
        StringBuilder validationMessage = new StringBuilder();
        Map<String, String> validationMessageObject = new HashMap<>();

        if (mainDescriptor.isPresent()) {
            try {
                Yaml safeYaml = new Yaml(new SafeConstructor());
                // This should throw an exception if there are unexpected blocks
                safeYaml.load(mainDescriptor.get().getContent());
                safe = true;
            } catch (Exception e) {
                isValid = false;
                LOG.info("An unsafe YAML was attempted to be parsed");
                validationMessage.append("CWL file is malformed or missing, cannot extract metadata: " + e.getMessage());
            }
            if (safe) {
                Yaml yaml = new Yaml();
                String content = mainDescriptor.get().getContent();
                if (content == null || content.isEmpty()) {
                    isValid = false;
                    validationMessage.append("Primary descriptor is empty.");
                } else if (!content.contains("class: Workflow")) {
                    isValid = false;
                    validationMessage.append("A CWL workflow requires 'class: Workflow'.");
                    if (content.contains("class: CommandLineTool") || content.contains("class: ExpressionTool")) {
                        String cwlClass = content.contains("class: CommandLineTool") ? "CommandLineTool" : "ExpressionTool";
                        validationMessage.append(" This file contains 'class: ").append(cwlClass).append("'. Did you mean to register a tool?");
                    }
                } else if (!this.isValidCwl(content, yaml)) {
                    isValid = false;
                    validationMessage.append("Invalid CWL version.");
                }
            }
        } else {
            validationMessage.append("Primary CWL descriptor is not present.");
            isValid = false;
        }

        if (isValid) {
            return new VersionTypeValidation(true, Collections.emptyMap());
        } else {
            validationMessageObject.put(primaryDescriptorFilePath, validationMessage.toString());
            return new VersionTypeValidation(false, validationMessageObject);
        }
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        List<DescriptorLanguage.FileType> fileTypes = new ArrayList<>(Collections.singletonList(DescriptorLanguage.FileType.DOCKSTORE_CWL));
        Set<SourceFile> filteredSourceFiles = filterSourcefiles(sourcefiles, fileTypes);
        Optional<SourceFile> mainDescriptor = filteredSourceFiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath))).findFirst();

        boolean isValid = true;
        String validationMessage = null;
        Map<String, String> validationMessageObject = new HashMap<>();

        if (mainDescriptor.isPresent()) {
            Yaml yaml = new Yaml();
            String content = mainDescriptor.get().getContent();
            if (content == null || content.isEmpty()) {
                isValid = false;
                validationMessage = "Primary CWL descriptor is empty.";
            } else if (!content.contains("class: CommandLineTool") && !content.contains("class: ExpressionTool")) {
                isValid = false;
                validationMessage = "A CWL tool requires 'class: CommandLineTool' or 'class: ExpressionTool'.";
                if (content.contains("class: Workflow")) {
                    validationMessage += " This file contains 'class: Workflow'. Did you mean to register a workflow?";
                }
            } else if (!this.isValidCwl(content, yaml)) {
                isValid = false;
                validationMessage = "Invalid CWL version.";
            }
        } else {
            isValid = false;
            validationMessage = "Primary CWL descriptor is not present.";
        }

        validationMessageObject.put(primaryDescriptorFilePath, validationMessage);
        return new VersionTypeValidation(isValid, validationMessageObject);
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return checkValidJsonAndYamlFiles(sourceFiles, DescriptorLanguage.FileType.CWL_TEST_JSON);
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
     * <p>As the preprocessor expands the CWL, it tracks the current file, and for each entry (workflow or tool) it encounters,
     * it first ensures that the entry has a unique id (by assigning the missing or duplicate id to a UUID), then adds the
     * id-to-current-file-path relationship to a Map.  Later, a parser can query the Map via the getPath method to determine
     * what file the entry came from.
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
        private final Map<String, String> idToPath;
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
            this.idToPath = new HashMap<>();
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

                // If the map represents a workflow or tool, ensure that it has a unique ID, record the ID->path relationship, and determine the CWL version
                if (isEntry(map)) {
                    idToPath.put(setUniqueIdIfAbsent(map), stripLeadingSlashes(currentPath));
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

        private boolean isEntry(Map<String, Object> cwl) {
            String c = (String)cwl.get("class");
            return Objects.equals(c, "CommandLineTool") || Objects.equals(c, "ExpressionTool") || Objects.equals(c, "Workflow");
        }

        private String setUniqueIdIfAbsent(Map<String, Object> entryCwl) {
            String currentId = (String)entryCwl.get("id");
            if (currentId == null || idToPath.containsKey(currentId)) {
                entryCwl.put("id", java.util.UUID.randomUUID().toString());
            }
            return (String)entryCwl.get("id");
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

        private Object parse(String yaml) {
            new Yaml(new SafeConstructor()).load(yaml);
            return new Yaml().load(yaml);
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
         * This method can be overidden to implement alternative behavior, such as returning instead of throwing, allowing preprocessing to continue.
         * @param message a message decribing which "max" condition was met
         */
        public void handleMax(String message) {
            String fullMessage = "CWL might be recursive: " + message;
            LOG.error(fullMessage);
            throw new CustomWebApplicationException(fullMessage, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }

        /**
         * Determine the path of the file that contained the specified entry.
         * @param id entry identifier
         * @returns file path
         */
        public String getPath(String id) {
            return idToPath.get(id);
        }
    }
}
