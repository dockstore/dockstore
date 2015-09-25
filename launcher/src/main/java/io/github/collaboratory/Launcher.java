package io.github.collaboratory;

import org.apache.commons.cli.*;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by boconnor on 9/24/15.
 */
public class Launcher {

    Options options = null;
    CommandLineParser parser = null;
    CommandLine line = null;
    HierarchicalINIConfiguration config = null;
    Object json = null;

    public Launcher(String [ ] args) {

        // create the command line parser
        parser = setupCommandLineParser();

        // parse command line
        line = parseCommandLine(args);

        // now read in the INI file
        config = getINIConfig(line.getOptionValue("config"));

        // now read the JSON file
        json = parseDescriptor(line.getOptionValue("descriptor"));

        // pull Docker images
        pullDockerImages(json);

        // pull data files
        pullFiles("data", json);

        // pull input files
        pullFiles("inputs", json);

        // construct command
        constructCommand(json);

        // run command
        runCommand(json);

        // push output files
        pushOutputFiles(json);
    }

    private void pushOutputFiles(Object json) {

    }

    private void runCommand(Object json) {

    }

    private void constructCommand(Object json) {

    }

    private void pullFiles(String data, Object json) {

    }

    private void pullDockerImages(Object json) {

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
            System.out.println("CMD: docker pull "+image);
        }
    }

    private Object parseDescriptor(String descriptorFile) {

        JSONParser parser=new JSONParser();
        Object obj = null;

        try {
            obj = parser.parse(new FileReader(new File(descriptorFile)));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }

        return(null);
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
                    System.out.println("KEY: "+key+" VALUE: "+value);
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
            System.out.println( "Unexpected exception:" + exp.getMessage() );
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

    public static void main(String [ ] args)
    {

        Launcher l = new Launcher(args);

    }

}
