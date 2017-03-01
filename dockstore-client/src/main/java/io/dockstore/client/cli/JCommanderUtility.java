package io.dockstore.client.cli;

import java.util.List;
import java.util.Objects;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Strings;
import com.beust.jcommander.WrappedParameter;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;

/**
 * @author gluu
 * @since 01/03/17
 */
public final class JCommanderUtility {
    private JCommanderUtility() {
        // hide the constructor for utility classes
    }

    public static void printJCommanderHelp(JCommander jc, String programName, String commandName) {
        JCommander commander = jc.getCommands().get(commandName);
        String description = jc.getCommandDescription(commandName);
        List<ParameterDescription> sorted = commander.getParameters();

        printHelpHeader();
        printJCommanderHelpUsage(programName, commandName);
        printJCommanderHelpDescription(description);
        printJCommanderHelpRequiredParameters(sorted);
        printJCommanderHelpOptionalParameters(sorted);
        printJCommanderHelpFooter();
    }

    private static void printJCommanderHelpUsage(String programName, String commandName) {
        ArgumentUtility.out("Usage: " + programName + " " + commandName + " --help");
        ArgumentUtility.out("       " + programName + " " + commandName + " [parameters]");
        ArgumentUtility.out("");
    }

    private static void printJCommanderHelpDescription(String commandDescription) {
        ArgumentUtility.out("Description:");
        ArgumentUtility.out("  " + commandDescription);
        ArgumentUtility.out("");
    }

    private static void printJCommanderHelpRequiredParameters(List<ParameterDescription> sorted) {
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (parameter.required() && !Objects.equals(pd.getNames(), "--help")) {
                if (first) {
                    ArgumentUtility.out("Required parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter);
            }
        }
        ArgumentUtility.out("");
    }

    private static void printJCommanderHelpOptionalParameters(List<ParameterDescription> sorted) {
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (!parameter.required() && !pd.getNames().equals("--help")) {
                if (first) {
                    ArgumentUtility.out("Optional parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter);
            }
        }
        ArgumentUtility.out("");
    }

    private static void printJCommanderHelpParameter(ParameterDescription pd, WrappedParameter parameter) {
        out("%-30s %s", "  " + pd.getNames() + " <" + pd.getNames().replaceAll("-", "") + ">", pd.getDescription());
        Object def = pd.getDefault();
        if (pd.isDynamicParameter()) {
            ArgumentUtility.out("Syntax: " + parameter.names()[0] + "key" + parameter.getAssignment() + "value");
        }
        if (def != null && !pd.isHelp()) {
            String displayedDef = Strings.isStringEmpty(def.toString()) ? "<empty string>" : def.toString();
            ArgumentUtility.out("Default: " + (parameter.password() ? "********" : displayedDef));
        }
    }

    private static void printJCommanderHelpFooter() {
        out("---------------------------------------------");
        out("");
    }
}
