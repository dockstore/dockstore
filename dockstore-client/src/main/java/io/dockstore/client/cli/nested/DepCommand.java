package io.dockstore.client.cli.nested;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.JCommanderUtility;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.api.MetadataApi;

import static io.dockstore.client.cli.Client.API_ERROR;

/**
 * @author gluu
 * @since 12/04/18
 */
public final class DepCommand {
    private DepCommand() {
    }

    /**
     * Handles when the deps command is called from the client
     *
     * @param args The command line arguments
     */
    public static boolean handleDepCommand(String[] args) {
        CommandDep commandDep = new CommandDep();
        JCommander jCommanderMain = new JCommander();
        JCommanderUtility.addCommand(jCommanderMain, "deps", commandDep);
        jCommanderMain.parse(args);
        if (commandDep.help) {
            JCommanderUtility.printJCommanderHelp(jCommanderMain, "dockstore", "deps");
        } else {
            ApiClient defaultApiClient;
            defaultApiClient = Configuration.getDefaultApiClient();
            MetadataApi metadataApi = new MetadataApi(defaultApiClient);
            String runnerDependencies = metadataApi
                    .getRunnerDependencies(commandDep.clientVersion, commandDep.pythonVersion, commandDep.runner, "text");
            if (runnerDependencies == null) {
                ArgumentUtility.errorMessage("Could not get runner dependencies", API_ERROR);
            } else {
                ArgumentUtility.out(runnerDependencies);
            }

        }
        return true;
    }

    @Parameters(separators = "=", commandDescription = "Print cwltool runner dependencies")
    private static class CommandDep {
        @Parameter(names = "--client-version", description = "Dockstore version")
        private String clientVersion = Client.getClientVersion();
        @Parameter(names = "--python-version", description = "Python version")
        private String pythonVersion = "2";
        // @Parameter(names = "--runner", description = "tool/workflow runner. Available options: 'cwltool'")
        private String runner = "cwltool";
        @Parameter(names = "--help", description = "Prints help for deps", help = true)
        private boolean help = false;
    }
}
