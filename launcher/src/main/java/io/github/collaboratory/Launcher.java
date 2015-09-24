package io.github.collaboratory;

import org.apache.commons.cli.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

/**
 * Created by boconnor on 9/24/15.
 */
public class Launcher {

    Options options = null;
    CommandLineParser parser = null;
    CommandLine line = null;

    public Launcher(String [ ] args) {

        // create the command line parser
        parser = setupCommandLineParser();

        // parse command line
        line = parseCommandLine(args);

        // now read in the INI file
        //HierarchicalINIConfiguration c = getINIConfig(line.getOptionValue("config"));

    }

    private HierarchicalINIConfiguration getINIConfig(String configFile) {
        InputStream is = null;
        try {
            is = new FileInputStream(new File(configFile));

        HierarchicalINIConfiguration c = new HierarchicalINIConfiguration();
        c.setEncoding("UTF-8");
        c.load(is);
        CharSequence doubleDot = "..";
        CharSequence dot = ".";

        Set<String> sections= c.getSections();
        for (String section: sections) {
            Class en = PropertiesConfigurationHelper.CONFIGURATION_SECTIONS.get(section);
            if (en == null) {
                Logger.warn(section + " is not a valid configuration section, it will be skipped!");
                continue;
            }
            SubnodeConfiguration subConf = c.getSection(section);
            Iterator<String> it = subConf.getKeys();
            while (it.hasNext()) {
                String key = (it.next());
                Object value = subConf.getString(key);
                key = key.replace(doubleDot, dot);
            }
        }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
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
