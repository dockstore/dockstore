package io.dockstore.webservice.core;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SourceFileTest {

    private static final List<String> GOOD_PATHS = List.of("/", ".", "-", "_", "abcz", "ABCZ", "01239", "some/good-path/under_score.cwl", ".dockstore.yml");

    /**
     * Generate a list of characters that are not allowed in SourceFile paths, in the range
     * from '\0' to an arbitrary character beyond the range of 8-bit ASCII.
     */
    private List<Character> computeBadChars() {
        List<Character> badChars = new ArrayList<>();
        for (int c = 0; c <= 300; c++) {
            boolean good =
                (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '/' || c == '.' || c == '-' || c == '_';
            if (!good) {
                badChars.add((char)c);
            }
        }
        return badChars;
    }

    private void testBadPath(String badPath) {
        SourceFile a = new SourceFile();
        try {
            a.setPath(badPath);
            Assert.fail("should have thrown the appropriate exception");
        } catch (CustomWebApplicationException e) {
            // expected execution path on successful test
        }
        SourceFile b = new SourceFile();
        try {
            b.setAbsolutePath(badPath);
            Assert.fail("should have thrown the appropriate exception");
        } catch (CustomWebApplicationException e) {
            // expected execution path on successful test
        }
    }

    @Test
    public void testSettingPaths() {

        for (String goodPath: GOOD_PATHS) {
            new SourceFile().setPath(goodPath);
            new SourceFile().setAbsolutePath(goodPath);

            for (char bad: computeBadChars()) {
                testBadPath(goodPath + bad);
                testBadPath(bad + goodPath);
                testBadPath(goodPath + bad + goodPath);
            }
        }
    }
}
