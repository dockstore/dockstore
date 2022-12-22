package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class SourceFileTest {

    private static final List<String> SAFE_PATHS = List.of("/", ".", "-", "_", "abcz", "ABCZ", "01239", "some/good-path/under_score.cwl", ".dockstore.yml");

    /**
     * Generate a list of characters that are not allowed in SourceFile paths, in the range
     * from '\0' to an arbitrary character beyond the range of 8-bit ASCII.
     */
    private List<Character> computeUnsafeChars() {
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
            fail("should have thrown the appropriate exception");
        } catch (CustomWebApplicationException e) {
            // expected execution path on successful test
        }
        SourceFile b = new SourceFile();
        try {
            b.setAbsolutePath(badPath);
            fail("should have thrown the appropriate exception");
        } catch (CustomWebApplicationException e) {
            // expected execution path on successful test
        }
    }

    @Test
    public void testSettingPathsUnrestricted() {
        SourceFile.unrestrictPaths();
        Random random = new Random(1234);
        for (int i = 0; i < 10000; i++) {
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j < 50; j++) {
                builder.append((char)random.nextInt());
            }
            String randomPath = builder.toString();
            new SourceFile().setPath(randomPath);
            new SourceFile().setAbsolutePath("/" + randomPath);
        }
    }

    @Test
    public void testSettingPathsRestricted() {
        SourceFile.restrictPaths(Pattern.compile("[-a-zA-Z0-9./_]*"), "Unsafe characters in path.");
        for (String goodPath: SAFE_PATHS) {
            new SourceFile().setPath(goodPath);
            new SourceFile().setAbsolutePath(goodPath);

            for (char bad: computeUnsafeChars()) {
                testBadPath(goodPath + bad);
                testBadPath(bad + goodPath);
                testBadPath(goodPath + bad + goodPath);
            }
        }
    }
}
