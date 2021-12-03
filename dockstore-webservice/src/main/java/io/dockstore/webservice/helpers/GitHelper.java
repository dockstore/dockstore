package io.dockstore.webservice.helpers;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class for handling git related problems
 * @author aduncan
 */
public final class GitHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GitHelper.class);

    private GitHelper() {

    }

    /**
     * Parse git references (ex. refs/heads/feature/foobar) and returns an optional reference name
     * @param gitReference Git Reference of the form refs/tags/name or refs/heads/name
     * @return Optional Git reference name
     */
    public static Optional<String> parseGitHubReference(String gitReference) {
        // Match the github reference (ex. refs/heads/feature/foobar or refs/tags/1.0)
        Pattern pattern = Pattern.compile("^refs/(tags|heads)/([a-zA-Z0-9]++([./_-]?[a-zA-Z0-9]+)*)$");
        Matcher matcher = pattern.matcher(gitReference);

        if (!matcher.find()) {
            return Optional.empty();
        }
        String gitBranchName = matcher.group(2);
        return Optional.of(gitBranchName);
    }
}
