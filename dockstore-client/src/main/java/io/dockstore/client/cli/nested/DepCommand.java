package io.dockstore.client.cli.nested;

import javax.ws.rs.core.MediaType;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.ArgumentUtility;
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

    public static void handleDepCommand(String[] args) {
        CommandDep commandDep = new CommandDep();
        JCommander jCommander = new JCommander(commandDep);
        jCommander.parse(args);
        if (commandDep.help) {
            jCommander.usage();
        } else {
            ApiClient defaultApiClient;
            String[] string = {MediaType.TEXT_PLAIN};
            defaultApiClient = Configuration.getDefaultApiClient();
            defaultApiClient.selectHeaderContentType(string);
            ContainersApi containersApi = new ContainersApi(defaultApiClient);
            String runnerDependencies = containersApi
                .getRunnerDependencies(commandDep.clientVersion, commandDep.pythonVersion, commandDep.runner);
            ArgumentUtility.out(runnerDependencies);
        }
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
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
