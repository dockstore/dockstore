package io.dockstore.webservice.helpers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.dockstore.webservice.CustomWebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;

/**
 * A helper class for handling git related problems
 * @author aduncan
 */
public final class GitHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GitHelper.class);

    private GitHelper() {

    }

    /**
     * Parse git references (ex. refs/heads/feature/foobar) and returns a reference name
     * @param gitReference Git Reference of the form refs/tags/name or refs/heads/name
     * @return Git reference name
     */
    public static String parseGitHubReference(String gitReference) {
        // Match the github reference (ex. refs/heads/feature/foobar or refs/tags/1.0)
        Pattern pattern = Pattern.compile("^refs/(tags|heads)/([a-zA-Z0-9]+([./_-]?[a-zA-Z0-9]+)*)$");
        Matcher matcher = pattern.matcher(gitReference);

        if (!matcher.find()) {
            String msg = "Reference " + gitReference + " is not of the valid form";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        String gitBranchName = matcher.group(2);
        return gitBranchName;
    }
}
