package io.github.collaboratory;

import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

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

    public Launcher(String [ ] args) {

        log = LogFactory.getLog(this.getClass());

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
        pullFiles("DATA", json);

        // pull input files
        pullFiles("INPUT", json);

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
        //TODO
    }

    private void pullFiles(String data, Object json) {

        log.info("DOWNLOADING "+data+" FILES...");

        // just figure out which files from the JSON are we pulling
        String type = null;
        if ("DATA".equals(data)) {
            type = "data";
        } else if ("INPUT".equals(data)) {
            type = "inputs";
        }

        // get list of files
        JSONArray files = (JSONArray) ((JSONObject) json).get(type);
        for (Object file : files) {

            String filePath = (String) ((JSONObject) file).get("path");
            String fileURL = (String) ((JSONObject) file).get("");

            // imagesHash.put(image.toString(), image.toString());
            log.info("FILE: ");

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
