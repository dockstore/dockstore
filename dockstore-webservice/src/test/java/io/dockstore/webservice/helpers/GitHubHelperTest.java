package io.dockstore.webservice.helpers;

import static io.dockstore.webservice.helpers.GitHubSourceCodeRepo.GIT_BRANCH_TAG_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class GitHubHelperTest {

    private static final String CODE = "abcdefghijklmnop";
    private static final String GITHUB_CLIENT_ID = "123456789abcd";
    private static final String GITHUB_CLIENT_SECRET = "98654321dcba";

    // Invalid GitHub authentication should return 400
    @Test
    void testGetGitHubAccessToken() {
        try {
            GitHubHelper.getGitHubAccessToken(CODE, GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET);
            fail("No CustomWebApplicationException thrown");
        } catch (CustomWebApplicationException e) {
            assertEquals("HTTP 400 Bad Request", e.getMessage());
        }
    }

    @Test
    void testGitHubBranchTagPattern() {
        testReferenceString("refs/heads/feature/foobar", "heads/feature/foobar");
        testReferenceString("refs/tags/1.0", "tags/1.0");
        testReferenceString("refs/heads/main", "heads/main");
        testReferenceString("refs/tags/v3.14.1", "tags/v3.14.1");
        testReferenceString("refs/heads/feature/foo_bar", "heads/feature/foo_bar");
        testReferenceString("refs/heads/feature/_leadingunderscore", "heads/feature/_leadingunderscore");
        testReferenceString("refs/heads/foo_bar", "heads/foo_bar");
        testReferenceString("refs/heads/_foo_bar", "heads/_foo_bar");
        testReferenceString("refs/heads/foo-bar", "heads/foo-bar");
        testReferenceString("refs/heads/-foo-bar", "heads/-foo-bar");
        testReferenceString("refs/heads/foo.bar", "heads/foo.bar");
        testReferenceString("refs/heads/.foo.bar", "heads/.foo.bar");
        testReferenceString("refs/heads/_leadingunderscore", "heads/_leadingunderscore");
    }

    private static void testReferenceString(String gitReference, String expectedString) {
        Matcher matcher = GIT_BRANCH_TAG_PATTERN.matcher(gitReference);
        final boolean b = matcher.find();
        String gitBranchType = matcher.group(1);
        String gitBranchName = matcher.group(2);
        assertEquals(expectedString, (gitBranchType + "/" + gitBranchName));
    }
}
