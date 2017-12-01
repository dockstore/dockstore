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

package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Workflow;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Organizes all methods that have to do with parsing of input and creation of output.
 * This is a static utility class with no state.
 *
 * @author dyuen
 */
public final class ArgumentUtility {

    public static final String CONVERT = "convert";
    public static final String LAUNCH = "launch";
    public static final String CWL_STRING = "cwl";
    public static final String WDL_STRING = "wdl";
    public static final String NAME_HEADER = "NAME";
    public static final String DESCRIPTION_HEADER = "DESCRIPTION";
    public static final String GIT_HEADER = "Git Repo";
    public static final int MAX_DESCRIPTION = 50;

    private static final Logger LOG = LoggerFactory.getLogger(ArgumentUtility.class);

    private ArgumentUtility() {
        // hide the constructor for utility classes
    }

    public static void outFormatted(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    public static void out(String arg) {
        System.out.println(arg);
    }

    static void err(String arg) {
        System.err.println(arg);
    }

    static void errFormatted(String format, Object... args) {
        System.err.println(String.format(format, args));
    }

    public static void kill(String format, Object... args) {
        errFormatted(format, args);
        throw new Kill();
    }

    public static void exceptionMessage(Exception exception, String message, int exitCode) {
        if (!"".equals(message)) {
            err(message);
        }
        err(ExceptionUtils.getStackTrace(exception));

        System.exit(exitCode);
    }

    public static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

    /**
     * @param bool primitive
     * @return the String "Yes" or "No"
     */
    public static String boolWord(boolean bool) {
        return bool ? "Yes" : "No";
    }

    /**
     * @param args
     * @param key
     * @return
     */
    public static List<String> optVals(List<String> args, String key) {
        List<String> vals = new ArrayList<>();

        for (int i = 0; i < args.size(); /** do nothing */
             i = i) {
            String s = args.get(i);
            if (key.equals(s)) {
                args.remove(i);
                if (i < args.size()) {
                    String val = args.remove(i);
                    if (!val.startsWith("--")) {
                        String[] ss = val.split(",");
                        if (ss.length > 0) {
                            vals.addAll(Arrays.asList(ss));
                            continue;
                        }
                    }
                }
                errorMessage("dockstore: missing required argument to " + key, Client.CLIENT_ERROR);
            } else {
                i++;
            }
        }

        return vals;
    }

    public static String optVal(List<String> args, String key, String defaultVal) {
        String val = defaultVal;

        List<String> vals = optVals(args, key);
        if (vals.size() == 1) {
            val = vals.get(0);
        } else if (vals.size() > 1) {
            errorMessage("dockstore: multiple instances of " + key, Client.CLIENT_ERROR);
        }

        return val;
    }

    public static String reqVal(List<String> args, String key) {
        String val = optVal(args, key, null);

        if (val == null) {
            errorMessage("dockstore: missing required flag " + key, Client.CLIENT_ERROR);
        }

        return val;
    }

    public static int[] columnWidthsTool(List<DockstoreTool> containers) {
        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (DockstoreTool container : containers) {
            final String toolPath = container.getToolPath();
            if (toolPath != null && toolPath.length() > maxWidths[0]) {
                maxWidths[0] = toolPath.length();
            }
            final String description = container.getDescription();
            if (description != null && description.length() > maxWidths[1]) {
                maxWidths[1] = description.length();
            }
            final String gitUrl = container.getGitUrl();
            if (gitUrl != null && gitUrl.length() > maxWidths[2]) {
                maxWidths[2] = gitUrl.length();
            }
        }

        maxWidths[1] = (maxWidths[1] > MAX_DESCRIPTION) ? MAX_DESCRIPTION : maxWidths[1];

        return maxWidths;
    }

    public static int[] columnWidthsWorkflow(List<Workflow> workflows) {
        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (Workflow workflow : workflows) {
            final String workflowGitPath = workflow.getPath();
            if (workflowGitPath != null && workflowGitPath.length() > maxWidths[0]) {
                maxWidths[0] = workflowGitPath.length();
            }
            final String description = workflow.getDescription();
            if (description != null && description.length() > maxWidths[1]) {
                maxWidths[1] = description.length();
            }
            final String gitUrl = workflow.getGitUrl();
            if (gitUrl != null && gitUrl.length() > maxWidths[2]) {
                maxWidths[2] = gitUrl.length();
            }
        }

        maxWidths[1] = (maxWidths[1] > MAX_DESCRIPTION) ? MAX_DESCRIPTION : maxWidths[1];

        return maxWidths;
    }

    static boolean isHelpRequest(String first) {
        return "-h".equals(first) || "--help".equals(first);
    }

    public static boolean containsHelpRequest(List<String> args) {
        boolean containsHelp = false;

        for (String arg : args) {
            if (isHelpRequest(arg)) {
                containsHelp = true;
                break;
            }
        }

        return containsHelp;
    }

    public static void printLineBreak() {
        out("---------------------------------------------");
    }

    public static void printHelpHeader() {
        out("");
        out(" ____             _        _                 \n" + "|  _ \\  ___   ___| | _____| |_ ___  _ __ ___ \n"
                + "| | | |/ _ \\ / __| |/ / __| __/ _ \\| '__/ _ \\\n" + "| |_| | (_) | (__|   <\\__ \\ || (_) | | |  __/\n"
                + "|____/ \\___/ \\___|_|\\_\\___/\\__\\___/|_|  \\___|\n");
        printLineBreak();
        out("See https://www.dockstore.org for more information");
        out("");
    }

    public static void printHelpFooter() {
        out("");
        printLineBreak();
        out("");
    }

    public static void invalid(String cmd) {
        errorMessage("dockstore: " + cmd + " is not a dockstore command. See 'dockstore --help'.", Client.CLIENT_ERROR);
    }

    private static void invalid(String cmd, String sub) {
        errorMessage("dockstore: " + cmd + " " + sub + " is not a dockstore command. See 'dockstore " + cmd + " --help'.",
                Client.CLIENT_ERROR);
    }

    static boolean flag(List<String> args, String flag) {

        boolean found = false;
        for (int i = 0; i < args.size(); i++) {
            if (flag.equals(args.get(i))) {
                if (found) {
                    kill("consonance: multiple instances of '%s'.", flag);
                } else {
                    found = true;
                    args.remove(i);
                }
            }
        }
        return found;
    }

    public static String getGitRegistry(String gitUrl) {
        if (gitUrl.contains("bitbucket")) {
            return "bitbucket";
        } else if (gitUrl.contains("github")) {
            return "github";
        } else if (gitUrl.contains("gitlab")) {
            return "gitlab";
        } else {
            return null;
        }
    }

    static class Kill extends RuntimeException {
    }

}
