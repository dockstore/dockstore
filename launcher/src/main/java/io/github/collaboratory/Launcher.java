package io.github.collaboratory;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
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
        String workingDir = setupDirectories(json);

        // pull Docker images
        pullDockerImages(json);

        // pull data files
        pullFiles("DATA", json, fileMap);

        // pull input files
        pullFiles("INPUT", json, fileMap);

        // construct command
        String command = constructCommand(json);

        // run command
        runCommand(json, fileMap, workingDir, command);

        // push output files
        // LEFT OFF HERE
        pushOutputFiles(json);
    }

    private String setupDirectories(Object json) {

        log.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString("working-directory");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        globalWorkingDir = workingDir+"/launcher-"+uuid.toString();
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString());
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/configs");
        execute("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/working");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/inputs");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/logs");
        execute("mkdir -p "+workingDir+"/launcher-"+uuid.toString()+"/outputs");

        return(new File(workingDir+"/launcher-"+uuid.toString()).getAbsolutePath());

    }

    private void runCommand(Object json, HashMap<String, HashMap<String, String>> fileMap, String workingDir, String command) {

        // TODO: this doesn't deal with multi-tools properly
        JSONArray tools = (JSONArray) ((JSONObject) json).get("tools");
        for (Object tool : tools) {

            // container output path
            String containerOutputPath = (String) ((JSONObject) tool).get("container_output_path");

            // container working path
            String containerWorkingPath = (String) ((JSONObject) tool).get("container_working_path");

            // image to actually use
            String image = (String) ((JSONObject) tool).get("image_name");

            StringBuilder sb = new StringBuilder();

            ArrayList<String> sba = new ArrayList<>();
            // TODO: probably want the bare minimum env vars so 'bash -lc' is not ideal
            sb.append("docker run ");
            sba.add("docker");
            sba.add("run");

            // deal with data
            JSONArray files = (JSONArray) ((JSONObject) tool).get("data");
            for (Object file : files) {
                String fileId = (String) ((JSONObject) file).get("id");
                String containerPath = (String) ((JSONObject) file).get("path");
                String localPath = fileMap.get(fileId).get("local_path");
                if (!containerPath.startsWith("/")) {
                    containerPath = containerWorkingPath + "/" + containerPath;
                }
                sb.append("-v " + localPath + ":" + containerPath + " ");
                sba.add("-v");
                sba.add(localPath+":"+containerPath);

            }
            // deal with inputs
            files = (JSONArray) ((JSONObject) tool).get("inputs");
            for (Object file : files) {
                String fileId = (String) ((JSONObject) file).get("id");
                String containerPath = (String) ((JSONObject) file).get("path");
                String localPath = fileMap.get(fileId).get("local_path");
                if (!containerPath.startsWith("/")) {
                    containerPath = containerWorkingPath + "/" + containerPath;
                }
                sb.append("-v " + localPath + ":" + containerPath + " ");
                sba.add("-v");
                sba.add(localPath+":"+containerPath);
            }

            // deal with outputs
            sb.append("-v " + workingDir + "/outputs:" + containerOutputPath + " ");
            sba.add("-v");
            sba.add(workingDir + "/outputs:" + containerOutputPath);

            // docker image to run and command
            sb.append(image + " /bin/bash -c '" + command + "'");
            sba.add(image);
            sba.add("/bin/bash");
            sba.add("-c");
            sba.add(command);

            // execute the constructed command
            log.info("DOCKER CMD TO RUN: " + sb.toString());

            // FIXME: not working!
            //execute(sb.toString());
            // had to switch to this style instead
            executeArr(sba);

        }

    }

    private void pushOutputFiles(Object json) {
        // LEFT OFF HERE
    }


    private String constructCommand(Object json) {

        log.info("CONSTRUCTING COMMAND: ");

        String finalCmd = null;

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
            Map<String, String> inputsHash = new HashMap<>();
            JSONArray files = (JSONArray) ((JSONObject) tool).get("inputs");
            for (Object file : files) {
                inputsHash.put((String) ((JSONObject)file).get("id"), (String) ((JSONObject)file).get("path"));
            }
            root.put("inputs", inputsHash);

            // data files
            Map<String, String> dataHash = new HashMap<>();
            files = (JSONArray) ((JSONObject) tool).get("data");
            for (Object file : files) {
                dataHash.put((String) ((JSONObject) file).get("id"), (String) ((JSONObject)file).get("path"));
            }
            root.put("data", dataHash);

            // outputs
            Map<String, String> outputsHash = new HashMap<>();
            files = (JSONArray) ((JSONObject) tool).get("outputs");
            for (Object file : files) {
                outputsHash.put((String) ((JSONObject) file).get("id"), (String) ((JSONObject)file).get("path"));
            }
            root.put("outputs", outputsHash);

            // config object for template
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);

            // now process the command line
            try {

                Template t = new Template("commandTemplate", new StringReader(commandTemplate), cfg);

                Writer out = new StringWriter();
                t.process(root, out);

                finalCmd = out.toString();

                log.info("FINAL COMMAND: "+finalCmd);

            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            } catch (TemplateException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }

            log.info("CMD TEMPLATE: "+commandTemplate);
            log.info("CMD TO RUN: "+finalCmd);
        }

        return(finalCmd);
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
                String fileId = (String) ((JSONObject) file).get("id");
                File filePathObj = new File(filePath);
                String newDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID().toString();
                execute("mkdir -p "+newDirectory);
                File newDirectoryFile = new File(newDirectory);
                String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

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
                    fileMap.put(fileId, new1);

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
            log.info("CMD: " + command);
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            log.info("CMD RETURN CODE: " + p.exitValue());
            log.info("CMD STDERR:"+ IOUtils.toString(p.getErrorStream(), Charset.defaultCharset()));
            log.info("CMD STDOUT:"+ IOUtils.toString(p.getInputStream(), Charset.defaultCharset()));

        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    private void executeArr (ArrayList<String> command) {
        try {
            //log.info("CMD: " + command);
            Process p = Runtime.getRuntime().exec(command.toArray(new String[0]));
            p.waitFor();
            log.info("CMD RETURN CODE: " + p.exitValue());
            log.info("CMD STDERR:"+ IOUtils.toString(p.getErrorStream(), Charset.defaultCharset()));
            log.info("CMD STDOUT:"+ IOUtils.toString(p.getInputStream(), Charset.defaultCharset()));

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
