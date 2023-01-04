package core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.dockstore.webservice.helpers.GitHelper;
import java.util.Optional;
import org.junit.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Unit tests for GitHelper class
 * @author aduncan
 */
public class GitHelperTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @Test
    public void testGitReferenceParsing() {
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
