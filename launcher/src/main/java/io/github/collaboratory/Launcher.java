package io.github.collaboratory;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by boconnor on 9/24/15.
 */
public class Launcher {

    private Log log = null;
    Options options = null;
    CommandLineParser parser = null;
    CommandLine line = null;
    HierarchicalINIConfiguration config = null;
    Object json = null;
    HashMap<String, HashMap<String, String>> fileMap = null;
    String globalWorkingDir = null;

    public Launcher(String [ ] args) {

        // logging
        log = LogFactory.getLog(this.getClass());

        // hashmap for files
        fileMap = new HashMap<String, HashMap<String, String>>();

        // create the command line parser
        parser = setupCommandLineParser();

        // parse command line
        line = parseCommandLine(args);

        // now read in the INI file
        config = getINIConfig(line.getOptionValue("config"));

        // now read the JSON file
        // TODO: support comments in the JSON
        json = parseDescriptor(line.getOptionValue("descriptor"));

        // setup directories
        setupDirectories(json);

        // pull Docker images
        pullDockerImages(json);

        // pull data files
        pullFiles("DATA", json, fileMap);

        // pull input files
        pullFiles("INPUT", json, fileMap);

        // LEFT OFF HERE: need to work on the Docker command construction next

        // construct command
        constructCommand(json);

        // run command
        runCommand(json);

        // push output files
        pushOutputFiles(json);
    }

    private void setupDirectories(Object json) {

        log.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString("working-directory");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        globalWorkingDir = workingDir+"/launcher-"+uuid.toString();
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString());
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/configs");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/working");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/inputs");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/logs");
    }

    private void pushOutputFiles(Object json) {
        //TODO
    }

    private void runCommand(Object json) {
        //TODO
    }

    private void constructCommand(Object json) {

        log.info("CONSTRUCTING COMMAND: ");

        // TODO: this doesn't deal with multi-tools properly
        JSONArray tools = (JSONArray) ((JSONObject) json).get("tools");
        for (Object tool : tools) {

            // get command 
            String commandTemplate = (String) ((JSONObject) tool).get("command");

            // construct a HashMap with data and inputs documented in
            Map<String, Object> root = new HashMap<>();

            // container output path
            root.put("container_output_path", (String) ((JSONObject) tool).get("container_output_path"));

            // container working path
            root.put("container_working_path", (String) ((JSONObject) tool).get("container_working_path"));

            // inputs
            Map<String, Map> inputsHash = new HashMap<>();
            JSONArray files = (JSONArray) ((JSONObject) tool).get("inputs");
            for (Object file : files) {

                Map<String, String> fileHash = new HashMap<>();
                fileHash.put("url", (String) ((JSONObject)file).get("url"));
                fileHash.put("path", (String) ((JSONObject)file).get("path"));
                inputsHash.put((String) ((JSONObject)file).get("id"), fileHash);
                
            }
            root.put("inputs", inputsHash);

            // data files
            Map<String, Map> dataHash = new HashMap<>();
            files = (JSONArray) ((JSONObject) tool).get("data");
            for (Object file : files) {

                Map<String, String> fileHash = new HashMap<>();
                fileHash.put("url", (String) ((JSONObject)file).get("url"));
                fileHash.put("path", (String) ((JSONObject)file).get("path"));
                dataHash.put((String) ((JSONObject) file).get("id"), fileHash);

            }
            root.put("data", dataHash);

            // outputs
            Map<String, Map> outputsHash = new HashMap<>();
            files = (JSONArray) ((JSONObject) tool).get("outputs");
            for (Object file : files) {

                Map<String, String> fileHash = new HashMap<>();
                fileHash.put("url", (String) ((JSONObject)file).get("url"));
                fileHash.put("path", (String) ((JSONObject)file).get("path"));
                outputsHash.put((String) ((JSONObject) file).get("id"), fileHash);

            }
            root.put("outputs", outputsHash);

            // config object for template
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);

            // now process the command line
            try {

                Template t = new Template("templateName", new StringReader(commandTemplate), cfg);

                Writer out = new StringWriter();
                t.process(root, out);

                String transformedTemplate = out.toString();

            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            } catch (TemplateException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }

            log.info("CMD TO RUN: "+commandTemplate);
        }

    }

    private void pullFiles(String data, Object json, HashMap<String, HashMap<String, String>> fileMap) {

        log.info("DOWNLOADING "+data+" FILES...");

        // working dir

        // just figure out which files from the JSON are we pulling
        String type = null;
        if ("DATA".equals(data)) {
            type = "data";
        } else if ("INPUT".equals(data)) {
            type = "inputs";
        }

        log.info("TYPE: "+type);

        // for each tool
        // TODO: really this launcher will operate off of a request for a particular tool whereas the collab.json can define multiple tools
        // TODO: really don't want to process inputs for all tools!  Just the one going to be called
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
                File filePathObj = new File(filePath);
                String newDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID().toString();
                execute("mkdir -p "+newDirectory);
                String uuidPath = newDirectory + "/" + filePathObj.getName();

                // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and https://commons.apache.org/proper/commons-vfs/filesystems.html
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
                    fileMap.put(filePath, new1);

                } catch (FileSystemException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }

                log.info("FILE: LOCAL: " + filePath + " URL: " + fileURL);

            }
        }

    }

    private void pullDockerImages(Object json) {

        log.info("PULLING DOCKER CONTAINERS...");

        // list of images to pull
        HashMap<String, String> imagesHash = new HashMap<String, String>();

        // get list of images
        JSONArray tools = (JSONArray) ((JSONObject) json).get("tools");
        for (Object tool : tools) {
            JSONArray images = (JSONArray) ((JSONObject) tool).get("images");
            for (Object image : images) {
                imagesHash.put(image.toString(), image.toString());
            }
        }

        // pull the images
        for(String image : imagesHash.keySet()) {
            String command = "docker pull " + image;
            execute(command);
        }
    }

    private Object parseDescriptor(String descriptorFile) {

        JSONParser parser=new JSONParser();
        Object obj = null;

        // TODO: would be nice to support comments

        try {
            obj = parser.parse(new FileReader(new File(descriptorFile)));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }

        return(obj);
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

            Set<String> sections= c.getSections();
            for (String section: sections) {

                SubnodeConfiguration subConf = c.getSection(section);
                Iterator<String> it = subConf.getKeys();
                while (it.hasNext()) {
                    String key = (it.next());
                    Object value = subConf.getString(key);
                    key = key.replace(doubleDot, dot);
                    log.info("KEY: "+key+" VALUE: "+value);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        return(c);
    }

    private CommandLine parseCommandLine(String[] args) {

        try {
            // parse the command line arguments
            line = parser.parse( options, args );

        }
        catch( ParseException exp ) {
            log.error("Unexpected exception:" + exp.getMessage());
        }

        return(line);
    }

    private CommandLineParser setupCommandLineParser () {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        options = new Options();

        options.addOption( "c", "config", true, "the INI config file for this tool" );
        options.addOption( "d", "descriptor", true, "a JSON tool descriptor used to construct the command and run it" );

        return parser;
    }

    private void execute (String command) {
        try {
            log.info("CMD: "+command);
            Runtime.getRuntime().exec(command).waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    public static void main(String [ ] args)
    {

        Launcher l = new Launcher(args);

    }

}
