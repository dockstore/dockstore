package io.github.collaboratory;

import com.amazonaws.util.IOUtils;
import com.google.common.base.Joiner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.json.simple.JSONObject;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.composer.ComposerException;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author boconnor 9/24/15
 * @author dyuen
 */
public class LauncherCWL {

    private Log log = LogFactory.getLog(this.getClass());
    Options options = null;
    CommandLineParser parser = null;
    CommandLine line = null;
    HierarchicalINIConfiguration config = null;
    Map<String, Object> cwl = null;
    Map<String, Map<String, Object>> inputsAndOutputsJson = null;
    Map<String, Map<String, String>> fileMap = null;
    Map<String, Map<String, String>> outputMap = null;
    String globalWorkingDir = null;
    Yaml yaml = new Yaml(new SafeConstructor());

    public LauncherCWL(String[] args) {

        // hashmap for files
        fileMap = new HashMap<>();
        outputMap = new HashMap<>();

        // create the command line parser
        parser = setupCommandLineParser();

        // parse command line
        line = parseCommandLine(args);

        // now read in the INI file
        try {
            config = new HierarchicalINIConfiguration(new File(line.getOptionValue("config")));
        } catch (ConfigurationException e) {
            throw new RuntimeException("could not read launcher config ini", e);
        }

        // parse the CWL tool definition
        cwl = parseCWL(line.getOptionValue("descriptor"));

        if (cwl == null) {
            log.info("CWL was null");
            return;
        }

        if (!(cwl.get("class")).equals("CommandLineTool")) {
            log.info("Must be CommandLineTool");
            return;
        }

        // this is the job parameterization, just a JSON, defines the inputs/outputs in terms or real URLs that are provisioned by the launcher
        inputsAndOutputsJson = loadJob(line.getOptionValue("job"));

        if (inputsAndOutputsJson == null) {
            log.info("Cannot load job object.");
            return;
        }

        // setup directories
        //String workingDir = setupDirectories(cwl);
        globalWorkingDir = setupDirectories();

        // pull input files
        pullFiles("inputs", cwl, inputsAndOutputsJson, fileMap);

        // prep outputs, just creates output dir and records what the local output path will be
        prepUploads("outputs", cwl, inputsAndOutputsJson, outputMap);

        // create updated JSON inputs document
        String newJsonPath = createUpdatedInputsAndOutputsJson(fileMap, outputMap, inputsAndOutputsJson);

        // run command
        log.info("RUNNING COMMAND");
        Map<String, Object> outputObj = runCommand(line.getOptionValue("descriptor"), newJsonPath, globalWorkingDir+"/outputs/");

        //log.info(outputObj);

        // push output files
        pushOutputFiles(outputMap, outputObj);

    }

    private void prepUploads(String type, Object cwl, Object inputsOutputs, Map<String, Map<String, String>> fileMap) {

        log.info("PREPPING UPLOADS...");

        log.info(((Map) cwl).get(type));

        List files = (List) ((Map)cwl).get(type);

        // for each file input from the CWL
        for (Object file : files) {

            // pull back the name of the input from the CWL
            log.info(file.toString());
            log.info("FILE: " + ((Map) file).get("id"));
            String fileUrl;
            try {
                //fileUrl = new URL((String)((Map)file).get("id")).getRef();
                fileUrl = new URL((String)((Map)file).get("id")).getRef();
                log.info("REF: "+fileUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new RuntimeException("Could not process input URL",e);
            }

            if (fileUrl == null) {
                log.error("fileURL from the CWL was not able to be parsed for the file name as a ref");
            }

            // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
            log.info("JSON: "+inputsOutputs.toString());
            for (Object paramName : ((HashMap)inputsOutputs).keySet()) {
                HashMap param = (HashMap)((HashMap)inputsOutputs).get(paramName);
                String path = (String)param.get("path");

                if (paramName.equals(fileUrl)) {

                    // if it's the current one
                    log.info("PATH TO UPLOAD TO: "+path+" FOR "+fileUrl+" FOR "+paramName);

                    // output
                    // TODO: poor naming here, need to cleanup the variables
                    // just file name
                    // the file URL
                    File filePathObj = new File(fileUrl);
                    //String newDirectory = globalWorkingDir + "/outputs/" + UUID.randomUUID().toString();
                    String newDirectory = globalWorkingDir + "/outputs";
                    execute("mkdir -p " + newDirectory);
                    File newDirectoryFile = new File(newDirectory);
                    String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

                    // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                    // https://commons.apache.org/proper/commons-vfs/filesystems.html

                    // now add this info to a hash so I can later reconstruct a docker -v command
                    HashMap<String, String> new1 = new HashMap<>();
                    new1.put("local_path", uuidPath);
                    new1.put("docker_path", fileUrl);
                    new1.put("url", path);
                    fileMap.put(fileUrl, new1);

                    log.info("UPLOAD FILE: LOCAL: " + fileUrl + " URL: " + path);

                }
            }

        }
    }

    private String createUpdatedInputsAndOutputsJson(Map<String, Map<String, String>> fileMap, Map<String, Map<String, String>> outputMap, Map<String, Map<String, Object>> inputsAndOutputsJson) {

        JSONObject newJSON = new JSONObject();

        for (String paramName : inputsAndOutputsJson.keySet()) {
            Map<String, Object> param = inputsAndOutputsJson.get(paramName);
            String path = (String) param.get("path");
            log.info("PATH: "+path+" PARAM_NAME: "+paramName);
            // will be null for output
            if (fileMap.get(paramName) != null) {
                param.put("path", fileMap.get(paramName).get("local_path"));
                log.info("NEW FULL PATH: " + fileMap.get(paramName).get("local_path"));
            } else if (outputMap.get(paramName) != null) {
                param.put("path", outputMap.get(paramName).get("local_path"));
                log.info("NEW FULL PATH: " + outputMap.get(paramName).get("local_path"));
            }
            // now add to the new JSON structure
            JSONObject newRecord = new JSONObject();
            newRecord.put("class", param.get("class"));
            newRecord.put("path", param.get("path"));
            newJSON.put(paramName, newRecord);
        }

        writeJob("foo.json", newJSON);

        return("foo.json");
    }

    private Map<String, Map<String, Object>> loadJob(String jobPath) {
        try {
            return (Map<String, Map<String, Object>>)yaml.load(new FileInputStream(jobPath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not load job from yaml",e);
        }
    }

    private void writeJob(String jobOutputPath, JSONObject newJson) {
        try {
            //TODO: investigate, why is this replacement occurring?
            final String replace = newJson.toJSONString().replace("\\", "");
            FileUtils.writeStringToFile(new File(jobOutputPath), replace, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write job ", e);
        }
    }

    private String setupDirectories() {

        log.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString("working-directory");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        globalWorkingDir = workingDir + "/launcher-" + uuid.toString();
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString());
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/configs");
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/working");
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/inputs");
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/logs");
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/outputs");

        return (new File(workingDir + "/launcher-" + uuid.toString()).getAbsolutePath());

    }

    private Map<String, Object> runCommand(String cwlFile, String jsonSettings, String workingDir) {
        String[] s = new String[]{"cwltool", "--outdir", workingDir, cwlFile, jsonSettings};
        try {
            Process p = Runtime.getRuntime().exec(s);
            Map<String, Object> obj = (Map<String, Object>)yaml.load(p.getInputStream());
            p.waitFor();

            if (p.exitValue() != 0) {
                log.warn("Got return code " + p.exitValue());
                log.warn("Error message is: " + IOUtils.toString(p.getErrorStream()));
            }
            return obj;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("could not run cwl command " + Joiner.on(" ").join(Arrays.asList(s)), e);
        }
    }

    private void pushOutputFiles(Map<String, Map<String, String>> fileMap, Map<String, Object> outputObject) {

        log.info("UPLOADING FILES...");

        for (String fileName : fileMap.keySet()) {
            Map file = fileMap.get(fileName);

            String cwlOutputPath = (String)((Map)((Map)outputObject).get(fileName)).get("path");

            log.info("NAME: " + file.get("local_path") + " URL: " + file.get("url") +" FILENAME: "+fileName+" CWL OUTPUT PATH: "+cwlOutputPath);


            try {
                FileSystemManager fsManager;
                // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                fsManager = VFS.getManager();
                FileObject dest = fsManager.resolveFile((String)file.get("url"));
                FileObject src = fsManager.resolveFile(new File(cwlOutputPath).getAbsolutePath());
                dest.copyFrom(src, Selectors.SELECT_SELF);
            } catch (FileSystemException e) {
                throw new RuntimeException("Could not provision output files", e);
            }


        }

    }

    private void execute(String command) {
        try {
            log.info("CMD: " + command);
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            log.info("CMD RETURN CODE: " + p.exitValue());
            log.info("CMD STDERR:" + IOUtils.toString(p.getErrorStream()));
            log.info("CMD STDOUT:" + IOUtils.toString(p.getInputStream()));

        } catch (InterruptedException | IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Could not execute " + command, e);
        }
    }

    private void pullFiles(String type, Object cwl, Object inputsOutputs, Map<String, Map<String, String>> fileMap) {

        log.info("DOWNLOADING INPUT FILES...");

        // !(((Map)cwl).get("class")).equals("CommandLineTool")

        log.info(((Map) cwl).get("inputs"));

        List files = (List) ((Map)cwl).get(type);

        // for each file input from the CWL
        for (Object file : files) {

            // pull back the name of the input from the CWL
            log.info(file.toString());
            log.info("FILE: " + ((Map) file).get("id"));
            String fileUrl = null;
            try {
                fileUrl = new URL((String)((Map)file).get("id")).getRef();
                log.info("REF: "+fileUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }

            if (fileUrl == null) {
                log.error("fileURL from the CWL was not able to be parsed for the file name as a ref");
            }

            // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
            log.info("JSON: "+inputsOutputs.toString());
            for (Object paramName : ((HashMap)inputsOutputs).keySet()) {
                HashMap param = (HashMap)((HashMap)inputsOutputs).get(paramName);
                String path = (String)param.get("path");

                if (paramName.equals(fileUrl)) {

                    // if it's the current one
                    log.info("PATH TO DOWNLOAD FROM: "+path+" FOR "+fileUrl+" FOR "+paramName);

                    // output
                    // TODO: poor naming here, need to cleanup the variables
                    // just file name
                    // the file URL
                    File filePathObj = new File(fileUrl);
                    String newDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID().toString();
                    execute("mkdir -p " + newDirectory);
                    File newDirectoryFile = new File(newDirectory);
                    String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

                    // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                    // https://commons.apache.org/proper/commons-vfs/filesystems.html
                    FileSystemManager fsManager;
                    try {

                        // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                        fsManager = VFS.getManager();
                        FileObject src = fsManager.resolveFile(path);
                        FileObject dest = fsManager.resolveFile(new File(uuidPath).getAbsolutePath());
                        dest.copyFrom(src, Selectors.SELECT_SELF);

                        // now add this info to a hash so I can later reconstruct a docker -v command
                        Map<String, String> new1 = new HashMap<>();
                        new1.put("local_path", uuidPath);
                        new1.put("docker_path", fileUrl);
                        new1.put("url", path);
                        fileMap.put(fileUrl, new1);

                    } catch (FileSystemException e) {
                        log.error(e.getMessage());
                        throw new RuntimeException("Could not provision input files", e);
                    }

                    log.info("DOWNLOADED FILE: LOCAL: " + fileUrl + " URL: " + path);

                }
            }

        }

    }


    private Map<String, Object> parseCWL(String cwlFile) {
        try {
            Map<String, Object> obj;
            String[] s = new String[]{"cwltool", "--print-pre", cwlFile };
            Process p = Runtime.getRuntime().exec(s);
            obj = (Map<String, Object>)yaml.load(p.getInputStream());
            p.waitFor();

            if (p.exitValue() != 0) {
                log.warn("Got return code " + p.exitValue());
                log.warn("Error message is: " + IOUtils.toString(p.getErrorStream()));
            }
            return obj;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("Could not parse CWL", e);
        } catch (ComposerException e){
            throw new RuntimeException("Must be single object at root", e);
        }
    }

    private CommandLine parseCommandLine(String[] args) {
        try {
            // parse the command line arguments
            line = parser.parse(options, args);

        } catch (ParseException exp) {
            log.error("Unexpected exception:" + exp.getMessage());
            throw new RuntimeException("Could not parse command-line", exp);
        }
        return line;
    }

    private CommandLineParser setupCommandLineParser() {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        options = new Options();

        options.addOption("c", "config", true, "the INI config file for this tool");
        options.addOption("d", "descriptor", true, "a CWL tool descriptor used to construct the command and run it");
        options.addOption("j", "job", true, "a JSON parameterization of the CWL tool, includes URLs for inputs and outputs");

        return parser;
    }


    public static void main(String[] args) {
        new LauncherCWL(args);
    }

}
