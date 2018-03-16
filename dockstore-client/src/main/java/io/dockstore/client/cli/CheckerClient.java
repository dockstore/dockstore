package io.dockstore.client.cli;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import io.dockstore.client.cli.nested.WorkflowClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Entry;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printFlagHelp;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.printUsageHelp;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;

/**
 * This implements all operations on the CLI that are specific to checkers
 * @author aduncan
 */
public class CheckerClient extends WorkflowClient {

    public CheckerClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        super(workflowApi, usersApi, client, isAdmin);
    }

    @Override
    public void printGeneralHelp() {
        printHelpHeader();
        printUsageHelp(getEntryType().toLowerCase());

        // Checker client help
        out("Commands:");
        out("");
        out("  add   :  Adds a checker workflow to and existing tool/workflow.");
        out("");

        if (isAdmin) {
            printAdminHelp();
        }

        printLineBreak();
        printFlagHelp();
        printHelpFooter();
    }

    @Override
    public String getEntryType() {
        return "Checker";
    }

    @Override
    public boolean processEntryCommands(List<String> args, String activeCommand) throws IOException, ApiException {
        if (null != activeCommand) {
            switch (activeCommand) {
            case "add":
                addChecker(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    private void addChecker(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            addCheckerHelp();
        } else {
            // Retrieve arguments
            String entryPath = reqVal(args, "--entry");
            String descriptorType = optVal(args, "--descriptor-type", "cwl");
            String descriptorPath = reqVal(args, "--descriptor-path");
            String inputParameterPath = optVal(args, "--input-parameter-path", null);

            // Check that descriptor type is valid
            descriptorType = descriptorType.toLowerCase();
            if (!Objects.equals(descriptorType, CWL_STRING) && !Objects.equals(descriptorType, WDL_STRING)) {
                errorMessage("The given descriptor type " + descriptorType + " is not valid.",
                    Client.CLIENT_ERROR);
            }

            // Check that descriptor path is valid
            if (!descriptorPath.startsWith("/")) {
                errorMessage("Descriptor paths must be absolute paths.",
                    Client.CLIENT_ERROR);
            }

            // Check that input parameter path is valid
            if (inputParameterPath != null && !inputParameterPath.startsWith("/")) {
                errorMessage("Input parameter path paths must be absolute paths.",
                    Client.CLIENT_ERROR);
            }

            // Get entry from path
            Entry entry = null;
            try {
                entry = workflowsApi.getEntryByPath(entryPath);
            } catch (ApiException ex) {
                exceptionMessage(ex, "Could not find the entry with path" + entryPath, Client.API_ERROR);
            }

            // Register the checker workflow
            if (entry != null) {
                try {
                    workflowsApi.registerCheckerWorkflow(descriptorPath, entry.getId(), descriptorType, inputParameterPath);
                    out("A checker workflow has been successfully added to entry with path " + entryPath);
                } catch (ApiException ex) {
                    exceptionMessage(ex, "There was a problem registering the checker workflow.", Client.API_ERROR);
                }
            }
        }
    }

    private void addCheckerHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " add --help");
        out("       dockstore " + getEntryType().toLowerCase() + " add [parameters]");
        out("");
        out("Description:");
        out("  Add a checker workflow to an existing tool or workflow.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                                          Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --descriptor-type <descriptor-type>                                      cwl/wdl, defaults to cwl.");
        out("  --descriptor-path <descriptor-path>                                      Path to the main descriptor file.");
        out("");
        out("Optional Parameters:");
        out("  --input-parameter-path <input parameter path>                            Path to the input parameter path, defaults to that of the entry.");
        printHelpFooter();
    }



}
