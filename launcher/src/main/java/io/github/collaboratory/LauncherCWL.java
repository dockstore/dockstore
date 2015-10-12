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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
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
    String globalWorkingDir = null;
    Yaml yaml = new Yaml(new SafeConstructor());

    public LauncherCWL(String[] args) {

        // hashmap for files
        fileMap = new HashMap<>();

        // create the command line parser
        parser = setupCommandLineParser();

        // parse command line
        line = parseCommandLine(args);

        // now read in the INI file
        config = getINIConfig(line.getOptionValue("config"));

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

        inputsAndOutputsJson = loadJob(line.getOptionValue("job"));

        if (inputsAndOutputsJson == null) {
            log.info("Cannot load job object.");
            return;
        }

        // setup directories
        String workingDir = setupDirectories(cwl);

        // pull data files
        //pullFiles("DATA", cwl, fileMap);

        // pull input files
        //pullFiles("INPUT", cwl, fileMap);

        // run command
        Object outputObj = runCommand(line.getOptionValue("descriptor"),
                                      inputsAndOutputsJson, workingDir);

        log.info(outputObj);

        // push output files
        //pushOutputFiles(cwl, fileMap, workingDir);

        // push output files
        //pushOutputFiles(json, fileMap, workingDir);
    }

    private Object loadJob(String jobPath) {
        try {
            return yaml.load(new FileInputStream(jobPath));
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }

    private String setupDirectories(Object json) {
        return "";
    }

    private Object runCommand(String cwlFile, Object inputObject, String workingDir) {
        Object obj = null;

        try {
            String[] s = new String[]{"cwltool", "--outdir", workingDir, cwlFile, "-"};
            Process p = Runtime.getRuntime().exec(s);
            yaml.dump(inputObject, new java.io.OutputStreamWriter(p.getOutputStream(), Charset.forName("UTF-8").newEncoder()));
            p.getOutputStream().close();
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

    private void pushOutputFiles(Object json, HashMap<String, HashMap<String, String>> fileMap, String workingDir) {

        log.info("UPLOADING FILES...");

        // for each tool
        // TODO: really this launcher will operate off of a request for a particular tool whereas the collab.json can define multiple tools
        // TODO: really don't want to process inputs for all tools! Just the one going to be called
        JSONArray tools = (JSONArray) ((JSONObject) json).get("tools");
        for (Object tool : tools) {

            // get list of files
            JSONArray files = (JSONArray) ((JSONObject) tool).get("outputs");

            log.info("files: " + files);

            for (Object file : files) {

                // output
                String fileURL = (String) ((JSONObject) file).get("url");

                // input
                String filePath = (String) ((JSONObject) file).get("path");
                String fileId = (String) ((JSONObject) file).get("id");
                // TODO: would be best to have output files here in this data structure rather than construct the path below
                // String localFilePath = fileMap.get(fileId).get("local_path");
                String localFilePath = workingDir + "/outputs/" + filePath;

                // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                // https://commons.apache.org/proper/commons-vfs/filesystems.html
                FileSystemManager fsManager = null;
                try {

                    // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                    fsManager = VFS.getManager();
                    FileObject dest = fsManager.resolveFile(fileURL);
                    FileObject src = fsManager.resolveFile(new File(localFilePath).getAbsolutePath());
                    dest.copyFrom(src, Selectors.SELECT_SELF);

                } catch (FileSystemException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }

                log.info("FILE: LOCAL PATH: " + localFilePath + " DOCKER PATH: " + filePath + " DEST URL: " + fileURL);

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

    private void pullFiles(String data, Object json, HashMap<String, HashMap<String, String>> fileMap) {

        log.info("DOWNLOADING " + data + " FILES...");

        // working dir

        // just figure out which files from the JSON are we pulling
        String type = null;
        if ("DATA".equals(data)) {
            type = "data";
        } else if ("INPUT".equals(data)) {
            type = "inputs";
        }

        log.info("TYPE: " + type);

        // for each tool
        // TODO: really this launcher will operate off of a request for a particular tool whereas the collab.json can define multiple tools
        // TODO: really don't want to process inputs for all tools! Just the one going to be called
        JSONArray tools = (JSONArray) ((JSONObject) json).get("tools");
        for (Object tool : tools) {

            // get list of files
            JSONArray files = (JSONArray) ((JSONObject) tool).get(type);

            log.info("files: " + files);

            for (Object file : files) {

                // input
                String fileURL = (String) ((JSONObject) file).get("url");

                // output
                String filePath = (String) ((JSONObject) file).get("path");
                String fileId = (String) ((JSONObject) file).get("id");
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
                    FileObject src = fsManager.resolveFile(fileURL);
                    FileObject dest = fsManager.resolveFile(new File(uuidPath).getAbsolutePath());
                    dest.copyFrom(src, Selectors.SELECT_SELF);

                    // now add this info to a hash so I can later reconstruct a docker -v command
                    HashMap<String, String> new1 = new HashMap<String, String>();
                    new1.put("local_path", uuidPath);
                    new1.put("docker_path", filePath);
                    new1.put("url", fileURL);
                    fileMap.put(fileId, new1);

                } catch (FileSystemException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }

                log.info("FILE: LOCAL: " + filePath + " URL: " + fileURL);

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
