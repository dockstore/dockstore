package core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.dockstore.webservice.helpers.GitHelper;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

/**
 * Unit tests for GitHelper class
 * @author aduncan
 */
public class GitHelperTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    void testGitReferenceParsing() {
        Optional<String> reference = GitHelper.parseGitHubReference("refs/heads/foobar");
        assertEquals("foobar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/heads/feature/foobar");
        assertEquals("feature/foobar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/tags/foobar");
        assertEquals("foobar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/tags/feature/foobar");
        assertEquals("feature/foobar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/heads/foo_bar");
        assertEquals("foo_bar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/tags/feature/foo-bar");
        assertEquals("feature/foo-bar", reference.get());

        reference = GitHelper.parseGitHubReference("refs/tags/feature/foobar12");
        assertEquals("feature/foobar12", reference.get());

        reference = GitHelper.parseGitHubReference("refs/fake/foobar");
        assertTrue(reference.isEmpty());

        reference = GitHelper.parseGitHubReference("refs/fake/feature/foobar");
        assertTrue(reference.isEmpty());

        reference = GitHelper.parseGitHubReference("feature/foobar");
        assertTrue(reference.isEmpty());
    }
}
