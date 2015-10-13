package io.github.collaboratory;

import com.amazonaws.util.IOUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    Object cwl = null;
    Object inputsAndOutputsJson = null;
    HashMap<String, HashMap<String, String>> fileMap = null;
    HashMap<String, HashMap<String, String>> outputMap = null;
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
        config = getINIConfig(line.getOptionValue("config"));

        // parse the CWL tool definition
        cwl = parseCWL(line.getOptionValue("descriptor"));

        if (cwl == null) {
            log.info("CWL was null");
            return;
        }

        if (!(cwl instanceof Map)) {
            log.info("Must be single object at root.");
            return;
        }

        if (!(((Map)cwl).get("class")).equals("CommandLineTool")) {
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
        globalWorkingDir = setupDirectories(cwl);

        // pull input files
        pullFiles("inputs", cwl, inputsAndOutputsJson, fileMap);

        // prep outputs, just creates output dir and records what the local output path will be
        prepUploads("outputs", cwl, inputsAndOutputsJson, outputMap);

        // create updated JSON inputs document
        String newJsonPath = createUpdatedInputsAndOutputsJson(fileMap, outputMap, inputsAndOutputsJson);

        // run command
        log.info("RUNNING COMMAND");
        Object outputObj = runCommand(line.getOptionValue("descriptor"), newJsonPath, globalWorkingDir+"/outputs/");

        //log.info(outputObj);

        // push output files
        pushOutputFiles(cwl, outputMap, globalWorkingDir+"/outputs/", outputObj);

    }

    private void prepUploads(String type, Object cwl, Object inputsOutputs, HashMap<String, HashMap<String, String>> fileMap) {

        log.info("PREPPING UPLOADS...");

        log.info(((Map) cwl).get(type));

        List files = (List) ((Map)cwl).get(type);

        // for each file input from the CWL
        for (Object file : files) {

            // pull back the name of the input from the CWL
            log.info(file.toString());
            log.info("FILE: " + ((Map) file).get("id"));
            String fileUrl = null;
            try {
                //fileUrl = new URL((String)((Map)file).get("id")).getRef();
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
            String fileURL = null;
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
                    String filePath = fileUrl;
                    // the file URL
                    String fileId = path;
                    File filePathObj = new File(filePath);
                    //String newDirectory = globalWorkingDir + "/outputs/" + UUID.randomUUID().toString();
                    String newDirectory = globalWorkingDir + "/outputs";
                    execute("mkdir -p " + newDirectory);
                    File newDirectoryFile = new File(newDirectory);
                    String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

                    // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                    // https://commons.apache.org/proper/commons-vfs/filesystems.html
                    FileSystemManager fsManager = null;

                    // now add this info to a hash so I can later reconstruct a docker -v command
                    HashMap<String, String> new1 = new HashMap<String, String>();
                    new1.put("local_path", uuidPath);
                    new1.put("docker_path", filePath);
                    new1.put("url", fileId);
                    fileMap.put(filePath, new1);

                    log.info("UPLOAD FILE: LOCAL: " + filePath + " URL: " + fileId);

                }
            }

        }
    }

    private String createUpdatedInputsAndOutputsJson(HashMap<String, HashMap<String, String>> fileMap, HashMap<String, HashMap<String, String>> outputMap, Object inputsAndOutputsJson) {

        JSONObject newJSON = new JSONObject();

        for (Object paramName : ((HashMap)inputsAndOutputsJson).keySet()) {
            HashMap param = (HashMap) ((HashMap) inputsAndOutputsJson).get(paramName);
            String path = (String) param.get("path");
            log.info("PATH: "+path+" PARAM_NAME: "+paramName);
            // will be null for output
            if (fileMap.get(paramName) != null) {
                param.put("path", ((HashMap) fileMap.get(paramName)).get("local_path"));
                log.info("NEW FULL PATH: "+ ((HashMap) fileMap.get(paramName)).get("local_path"));
            }
            else if (outputMap.get(paramName) != null) {
                param.put("path", ((HashMap) outputMap.get(paramName)).get("local_path"));
                log.info("NEW FULL PATH: "+ ((HashMap) outputMap.get(paramName)).get("local_path"));
            }
            //
            //
            //log.info("OLD: " + path + " NEW: " + (String) ((HashMap) fileMap.get(path)).get("local_path"));
            // now add to the new JSON structure
            JSONObject newRecord = new JSONObject();
            newRecord.put("class", param.get("class"));
            newRecord.put("path", param.get("path"));
            newJSON.put(paramName, newRecord);

        }

        writeJob("foo.json", newJSON);

        return("foo.json");
    }

    private Object loadJob(String jobPath) {
        try {
            return yaml.load(new FileInputStream(jobPath));
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }

    private void writeJob(String jobOutputPath, JSONObject newJson) {

        try {

            Writer file = new OutputStreamWriter(new FileOutputStream(jobOutputPath), "UTF-8");
            file.write(((JSONObject)newJson).toJSONString().replace("\\",""));
            file.flush();
            file.close();

            //execute("touch temp.json");
            //execute ("cat " + jobOutputPath + " | json_pp > temp.json");
            //execute("cp temp.json "+jobOutputPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String setupDirectories(Object json) {

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

    private Object runCommand(String cwlFile, String jsonSettings, String workingDir) {
        Object obj = null;

        try {
            String[] s = new String[]{"cwltool", "--outdir", workingDir, cwlFile, jsonSettings};
            Process p = Runtime.getRuntime().exec(s);
            //yaml.dump(inputObject, new java.io.OutputStreamWriter(p.getOutputStream(), Charset.forName("UTF-8").newEncoder()));
            //p.getOutputStream().close();
            obj = yaml.load(p.getInputStream());
            p.waitFor();

            if (p.exitValue() != 0) {
                log.warn("Got return code " + p.exitValue());
                log.warn("Error message is: " + IOUtils.toString(p.getErrorStream()));
            }
        } catch (java.lang.InterruptedException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        return (obj);
    }

    private void pushOutputFiles(Object json, HashMap<String, HashMap<String, String>> fileMap, String workingDir, Object outputObject) {

        log.info("UPLOADING FILES...");

        for (String fileName : fileMap.keySet()) {
            Map file = fileMap.get(fileName);

            String cwlOutputPath = (String)((Map)((Map)outputObject).get(fileName)).get("path");

            log.info("NAME: " + file.get("local_path") + " URL: " + file.get("url") +" FILENAME: "+fileName+" CWL OUTPUT PATH: "+cwlOutputPath);

            FileSystemManager fsManager = null;
            try {

                // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                fsManager = VFS.getManager();
                FileObject dest = fsManager.resolveFile((String)file.get("url"));
                FileObject src = fsManager.resolveFile(new File(cwlOutputPath).getAbsolutePath());
                dest.copyFrom(src, Selectors.SELECT_SELF);

            } catch (FileSystemException e) {
                e.printStackTrace();
                log.error(e.getMessage());
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

        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    private void pullFiles(String type, Object cwl, Object inputsOutputs, HashMap<String, HashMap<String, String>> fileMap) {

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
            String fileURL = null;
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
                    String filePath = fileUrl;
                    // the file URL
                    String fileId = path;
                    File filePathObj = new File(filePath);
                    String newDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID().toString();
                    execute("mkdir -p " + newDirectory);
                    File newDirectoryFile = new File(newDirectory);
                    String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

                    // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                    // https://commons.apache.org/proper/commons-vfs/filesystems.html
                    FileSystemManager fsManager = null;
                    try {

                        // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                        fsManager = VFS.getManager();
                        FileObject src = fsManager.resolveFile(fileId);
                        FileObject dest = fsManager.resolveFile(new File(uuidPath).getAbsolutePath());
                        dest.copyFrom(src, Selectors.SELECT_SELF);

                        // now add this info to a hash so I can later reconstruct a docker -v command
                        HashMap<String, String> new1 = new HashMap<String, String>();
                        new1.put("local_path", uuidPath);
                        new1.put("docker_path", filePath);
                        new1.put("url", fileId);
                        fileMap.put(filePath, new1);

                    } catch (FileSystemException e) {
                        e.printStackTrace();
                        log.error(e.getMessage());
                    }

                    log.info("DOWNLOADED FILE: LOCAL: " + filePath + " URL: " + fileId);

                }
            }

        }

    }


    private Object parseCWL(String cwlFile) {
        Object obj = null;

        try {
            String[] s = new String[]{"cwltool", "--print-pre", cwlFile };
            Process p = Runtime.getRuntime().exec(s);
            obj = yaml.load(p.getInputStream());
            p.waitFor();

            if (p.exitValue() != 0) {
                log.warn("Got return code " + p.exitValue());
                log.warn("Error message is: " + IOUtils.toString(p.getErrorStream()));
            }

        } catch (java.lang.InterruptedException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        return (obj);
    }

    private HierarchicalINIConfiguration getINIConfig(String configFile) {

        InputStream is = null;
        HierarchicalINIConfiguration c = null;

        try {

            is = new FileInputStream(new File(configFile));

            c = new HierarchicalINIConfiguration();
            c.setEncoding("UTF-8");
            c.load(is);
            CharSequence doubleDot = "..";
            CharSequence dot = ".";

            Set<String> sections = c.getSections();
            for (String section : sections) {

                SubnodeConfiguration subConf = c.getSection(section);
                Iterator<String> it = subConf.getKeys();
                while (it.hasNext()) {
                    String key = (it.next());
                    Object value = subConf.getString(key);
                    key = key.replace(doubleDot, dot);
                    log.info("KEY: " + key + " VALUE: " + value);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        return (c);
    }

    private CommandLine parseCommandLine(String[] args) {

        try {
            // parse the command line arguments
            line = parser.parse(options, args);

        } catch (ParseException exp) {
            log.error("Unexpected exception:" + exp.getMessage());
        }

        return (line);
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
