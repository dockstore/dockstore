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
package io.github.collaboratory.cwl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.Workflow;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.FileProvisioning;
import io.swagger.client.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.SCRIPT;

/**
 * Grouping code for launching CWL tools and workflows
 */
public class CWLClient implements LanguageClientInterface {

    private final AbstractEntryClient abstractEntryClient;

    public CWLClient(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }

    /**
     * @param entry        either a dockstore.cwl or a local file
     * @param isLocalEntry is the descriptor a local file
     * @param yamlRun      runtime descriptor, one of these is required
     * @param jsonRun      runtime descriptor, one of these is required
     * @param csvRuns      runtime descriptor, one of these is required
     * @param uuid         uuid that was optional specified for notifications
     * @throws IOException
     * @throws ApiException
     */
    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String csvRuns, String wdlOutputTarget,
        String uuid) throws IOException, ApiException {
        String originalTestParameterFilePath = abstractEntryClient.getOriginalTestParameterFilePath(yamlRun, jsonRun, csvRuns);
        if (!SCRIPT.get()) {
            abstractEntryClient.getClient().checkForCWLDependencies();
        }

        final File tempDir = Files.createTempDir();
        File tempCWL;
        if (!isLocalEntry) {
            try {
                tempCWL = abstractEntryClient.downloadDescriptorFiles(entry, "cwl", tempDir);
            } catch (ApiException e) {
                if (abstractEntryClient.getEntryType().toLowerCase().equals("tool")) {
                    exceptionMessage(e, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?",
                        ENTRY_NOT_FOUND);
                } else {
                    exceptionMessage(e, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                        ENTRY_NOT_FOUND);
                }
                throw new RuntimeException(e);
            }
        } else {
            tempCWL = new File(entry);
        }
        jsonRun = convertYamlToJson(yamlRun, jsonRun);

        try {
            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            if (jsonRun != null) {
                // translate jsonRun to absolute path
                if (Paths.get(jsonRun).toFile().exists()) {
                    jsonRun = Paths.get(jsonRun).toFile().getAbsolutePath();
                }

                // download jsonRun if remote
                JsonParser parser = new JsonParser();
                String jsonTempRun = File.createTempFile("parameter", "json").getAbsolutePath();
                FileProvisioning.retryWrapper(null, jsonRun, Paths.get(jsonTempRun), 1, true, 1);
                jsonRun = jsonTempRun;

                // if the root document is an array, this indicates multiple runs
                final JsonElement parsed = parser.parse(new InputStreamReader(new FileInputStream(jsonRun), StandardCharsets.UTF_8));
                if (parsed.isJsonArray()) {
                    final JsonArray asJsonArray = parsed.getAsJsonArray();
                    for (JsonElement element : asJsonArray) {
                        final String finalString = gson.toJson(element);
                        final File tempJson = File.createTempFile("parameter", ".json", Files.createTempDir());
                        FileUtils.write(tempJson, finalString, StandardCharsets.UTF_8);
                        final LauncherCWL cwlLauncher = new LauncherCWL(abstractEntryClient.getConfigFile(), tempCWL.getAbsolutePath(),
                            tempJson.getAbsolutePath(), null, null, originalTestParameterFilePath, uuid);
                        if (abstractEntryClient instanceof WorkflowClient) {
                            cwlLauncher.run(Workflow.class);
                        } else {
                            cwlLauncher.run(CommandLineTool.class);
                        }
                    }
                } else {
                    final LauncherCWL cwlLauncher = new LauncherCWL(abstractEntryClient.getConfigFile(), tempCWL.getAbsolutePath(), jsonRun,
                        null, null, originalTestParameterFilePath, uuid);
                    if (abstractEntryClient instanceof WorkflowClient) {
                        cwlLauncher.run(Workflow.class);
                    } else {
                        cwlLauncher.run(CommandLineTool.class);
                    }
                }
            } else if (csvRuns != null) {
                final File csvData = new File(csvRuns);
                try (CSVParser parser = CSVParser.parse(csvData, StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT.withDelimiter('\t').withEscape('\\').withQuoteMode(QuoteMode.NONE))) {
                    // grab header
                    final Iterator<CSVRecord> iterator = parser.iterator();
                    final CSVRecord headers = iterator.next();
                    // ignore row with type information
                    iterator.next();
                    // process rows
                    while (iterator.hasNext()) {
                        final CSVRecord csvRecord = iterator.next();
                        final File tempJson = File.createTempFile("temp", ".json", Files.createTempDir());
                        StringBuilder buffer = new StringBuilder();
                        buffer.append("{");
                        for (int i = 0; i < csvRecord.size(); i++) {
                            buffer.append("\"").append(headers.get(i)).append("\"");
                            buffer.append(":");
                            // if the type is an array, just pass it through
                            buffer.append(csvRecord.get(i));

                            if (i < csvRecord.size() - 1) {
                                buffer.append(",");
                            }
                        }
                        buffer.append("}");
                        // prettify it
                        JsonParser prettyParser = new JsonParser();
                        JsonObject json = prettyParser.parse(buffer.toString()).getAsJsonObject();
                        final String finalString = gson.toJson(json);

                        // write it out
                        FileUtils.write(tempJson, finalString, StandardCharsets.UTF_8);

                        // final String stringMapAsString = gson.toJson(stringMap);
                        // Files.write(stringMapAsString, tempJson, StandardCharsets.UTF_8);
                        final LauncherCWL cwlLauncher = new LauncherCWL(abstractEntryClient.getConfigFile(), tempCWL.getAbsolutePath(),
                            tempJson.getAbsolutePath(), null, null, originalTestParameterFilePath, uuid);
                        if (abstractEntryClient instanceof WorkflowClient) {
                            cwlLauncher.run(Workflow.class);
                        } else {
                            cwlLauncher.run(CommandLineTool.class);
                        }
                    }
                }
            } else {
                errorMessage("Missing required parameters, one of  --json or --tsv is required", CLIENT_ERROR);
            }
        } catch (CWL.GsonBuildException ex) {
            exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
        } catch (JsonParseException ex) {
            exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
        }
        return 0;
    }

    /**
     * this function will check if the content of the file is CWL or not
     * it will get the content of the file and try to find/match the required fields
     * Required fields in CWL: 'inputs' 'outputs' 'class' (CommandLineTool: 'baseCommand' , Workflow:'steps'
     * Optional field, but good practice: 'cwlVersion'
     *
     * @param content : the entry file content, type File
     * @return true if the file is CWL (warning will be added here if cwlVersion is not found but will still return true)
     * false if it's not a CWL file (could be WDL or something else)
     * errormsg & exit if >=1 required field not found in the file
     */
    @Override
    public Boolean check(File content) {
        /* CWL: check for 'class:CommandLineTool', 'inputs: ','outputs: ', and 'baseCommand'. Optional: 'cwlVersion'
         CWL: check for 'class:Workflow', 'inputs: ','outputs: ', and 'steps'. Optional: 'cwlVersion'*/
        Pattern inputPattern = Pattern.compile("(.*)(inputs)(.*)(:)(.*)");
        Pattern outputPattern = Pattern.compile("(.*)(outputs)(.*)(:)(.*)");
        Pattern classWfPattern = Pattern.compile("(.*)(class)(.*)(:)(\\sWorkflow)");
        Pattern classToolPattern = Pattern.compile("(.*)(class)(.*)(:)(\\sCommandLineTool)");
        Pattern commandPattern = Pattern.compile("(.*)(baseCommand)(.*)(:)(.*)");
        Pattern versionPattern = Pattern.compile("(.*)(cwlVersion)(.*)(:)(.*)");
        Pattern stepsPattern = Pattern.compile("(.*)(steps)(.*)(:)(.*)");
        String missing = "Required fields that are missing from CWL file :";
        boolean inputFound = false, classWfFound = false, classToolFound = false, outputFound = false, commandFound = false, versionFound = false, stepsFound = false;
        Path p = Paths.get(content.getPath());
        //go through each line of the file content and find the word patterns as described above
        try {
            List<String> fileContent = java.nio.file.Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : fileContent) {
                Matcher matchWf = classWfPattern.matcher(line);
                Matcher matchTool = classToolPattern.matcher(line);
                Matcher matchInput = inputPattern.matcher(line);
                Matcher matchOutput = outputPattern.matcher(line);
                Matcher matchCommand = commandPattern.matcher(line);
                Matcher matchVersion = versionPattern.matcher(line);
                Matcher matchSteps = stepsPattern.matcher(line);
                if (matchInput.find() && !stepsFound) {
                    inputFound = true;
                } else if (matchOutput.find()) {
                    outputFound = true;
                } else if (matchCommand.find()) {
                    commandFound = true;
                } else if (matchVersion.find()) {
                    versionFound = true;
                } else if (matchSteps.find()) {
                    stepsFound = true;
                } else {
                    if (abstractEntryClient.getEntryType().toLowerCase().equals("workflow") && matchWf.find()) {
                        classWfFound = true;
                    } else if (abstractEntryClient.getEntryType().toLowerCase().equals("tool") && matchTool.find()) {
                        classToolFound = true;
                    } else if ((abstractEntryClient.getEntryType().toLowerCase().equals("tool") && matchWf.find())) {
                        errorMessage("Expected a tool but the CWL file specified a workflow. Use 'dockstore workflow launch ...' instead.",
                            CLIENT_ERROR);
                    } else if (abstractEntryClient.getEntryType().toLowerCase().equals("workflow") && matchTool.find()) {
                        errorMessage("Expected a workflow but the CWL file specified a tool. Use 'dockstore tool launch ...' instead.",
                            CLIENT_ERROR);
                    }
                }
            }
            //check if the required fields are found, if not, give warning for the optional ones or error for the required ones
            if (inputFound && outputFound && classWfFound && stepsFound) {
                //this is a valid cwl workflow file
                if (!versionFound) {
                    out("Warning: 'cwlVersion' field is missing in the CWL file.");
                }
                return true;
            } else if (inputFound && outputFound && classToolFound && commandFound) {
                //this is a valid cwl tool file
                if (!versionFound) {
                    out("Warning: 'cwlVersion' field is missing in the CWL file.");
                }
                return true;
            } else if ((!inputFound && !outputFound && !classToolFound && !commandFound) || (!inputFound && !outputFound
                && !classWfFound)) {
                //not a CWL file, could be WDL or something else
                return false;
            } else {
                //CWL but some required fields are missing
                if (!outputFound) {
                    missing += " 'outputs'";
                }
                if (!inputFound) {
                    missing += " 'inputs'";
                }
                if (classWfFound && !stepsFound) {
                    missing += " 'steps'";
                }
                if (!classToolFound && !classWfFound) {
                    missing += " 'class'";
                }
                if (classToolFound && !commandFound) {
                    missing += " 'baseCommand'";
                }
                errorMessage(missing, CLIENT_ERROR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to get content of entry file.", e);
        }
        return false;

    }

    /**
     * @param entry Full path of the tool/workflow
     * @param json  Whether to return json or not
     * @return The json or tsv output
     * @throws ApiException
     * @throws IOException
     */
    public String generateInputJson(String entry, final boolean json) throws ApiException, IOException {
        final File tempDir = Files.createTempDir();
        final File primaryFile = abstractEntryClient.downloadDescriptorFiles(entry, CWL_STRING, tempDir);

        // need to suppress output
        final ImmutablePair<String, String> output = abstractEntryClient.getCwlUtil().parseCWL(primaryFile.getAbsolutePath());
        final Map<String, Object> stringObjectMap = abstractEntryClient.getCwlUtil().extractRunJson(output.getLeft());
        if (json) {
            try {
                final Gson gson = CWL.getTypeSafeCWLToolDocument();
                return gson.toJson(stringObjectMap);
            } catch (CWL.GsonBuildException ex) {
                exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
            } catch (JsonParseException ex) {
                exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
            }
        } else {
            // re-arrange as rows and columns
            final Map<String, String> typeMap = abstractEntryClient.getCwlUtil().extractCWLTypes(output.getLeft());
            final List<String> headers = new ArrayList<>();
            final List<String> types = new ArrayList<>();
            final List<String> entries = new ArrayList<>();
            for (final Map.Entry<String, Object> objectEntry : stringObjectMap.entrySet()) {
                headers.add(objectEntry.getKey());
                types.add(typeMap.get(objectEntry.getKey()));
                Object value = objectEntry.getValue();
                if (value instanceof Map) {
                    Map map = (Map)value;
                    if (map.containsKey("class") && "File".equals(map.get("class"))) {
                        value = map.get("path");
                    }

                }
                entries.add(value.toString());
            }
            final StringBuffer buffer = new StringBuffer();
            try (CSVPrinter printer = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
                printer.printRecord(headers);
                printer.printComment("do not edit the following row, describes CWL types");
                printer.printRecord(types);
                printer.printComment("duplicate the following row and fill in the values for each run you wish to set parameters for");
                printer.printRecord(entries);
            }
            return buffer.toString();
        }
        return null;
    }

    private String convertYamlToJson(String yamlRun, String jsonRun) throws IOException {
        // if we have a yaml parameter file, convert it into a json
        if (yamlRun != null) {
            final File tempFile = File.createTempFile("temp", "json");
            Yaml yaml = new Yaml();
            final FileInputStream fileInputStream = FileUtils.openInputStream(new File(yamlRun));
            Map<String, Object> map = (Map<String, Object>)yaml.load(fileInputStream);
            JSONObject jsonObject = new JSONObject(map);
            final String jsonContent = jsonObject.toString();
            FileUtils.write(tempFile, jsonContent, StandardCharsets.UTF_8);
            jsonRun = tempFile.getAbsolutePath();
        }
        return jsonRun;
    }
}
