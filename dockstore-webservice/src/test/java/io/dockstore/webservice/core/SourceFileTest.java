package io.dockstore.webservice.core;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SourceFileTest {

    private static final List<String> GOOD_PATHS = List.of("/", ".", "-", "_", "abcz", "ABCZ", "01239", "/good_path.swl", ".dockstore.yml");
    private static final List<String> BAD_PATHS = List.of("!", "~", "`", "'", "\n", " ", "$", "*", "\u0080", "/`bad_path`.cwl", "~/.dockstore.yml");

    @Test
    public void testSettingPaths() {

        for (String goodPath: GOOD_PATHS) {
            new SourceFile().setPath(goodPath);
            new SourceFile().setAbsolutePath(goodPath);
        }

        for (String badPath: BAD_PATHS) {
            try {
                new SourceFile().setPath(badPath);
                Assert.fail("should have thrown the appropriate exception");
            } catch (CustomWebApplicationException e) {
                // expected execution path on successful test
            }
            try {
                new SourceFile().setAbsolutePath(badPath);
                Assert.fail("should have thrown the appropriate exception");
            } catch (CustomWebApplicationException e) {
                // expected execution path on successful test
            }
        }
    }
}
