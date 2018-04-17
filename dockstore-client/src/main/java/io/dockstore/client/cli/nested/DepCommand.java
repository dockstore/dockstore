package io.dockstore.client.cli.nested;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.JCommanderUtility;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;

/**
 * @author gluu
 * @since 12/04/18
 */
public final class DepCommand {
    private DepCommand() {
    }

    /**
     * Handles when the deps command is called from the client
     * @param args     The command line arguments
     */
    public static void handleDepCommand(String[] args) {
        CommandDep commandDep = new CommandDep();
        JCommander jCommanderMain = new JCommander();
        JCommanderUtility.addCommand(jCommanderMain, "deps", commandDep);
        jCommanderMain.parse(args);
        if (commandDep.help) {
            JCommanderUtility.printJCommanderHelp(jCommanderMain, "dockstore tool", "deps");
        } else {
            ApiClient defaultApiClient;
            defaultApiClient = Configuration.getDefaultApiClient();
            ContainersApi containersApi = new ContainersApi(defaultApiClient);
            String runnerDependencies = containersApi
                    .getRunnerDependencies(commandDep.clientVersion, commandDep.pythonVersion, commandDep.runner, "text");
            ArgumentUtility.out(runnerDependencies);
        }
    }

    @Parameters(separators = "=", commandDescription = "Print tool runner dependencies")
    private static class CommandDep {
        @Parameter(names = "--client-version", description = "Dockstore version")
        private String clientVersion;
        @Parameter(names = "--python-version", description = "Python version")
        private String pythonVersion = "2";
        @Parameter(names = "--runner", description = "Tool runner")
        private String runner = "cwltool";
        @Parameter(names = "--help", description = "Prints help for deps", help = true)
        private boolean help = false;
    }
}
