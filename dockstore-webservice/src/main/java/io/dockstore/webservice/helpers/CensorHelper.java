package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects input Strings for long runs of base64 characters and X's them out if they appear to
 * be keys.  Useful for logging purposes.
 *
 * CensorHelper identifies potential secrets as longer substrings composed of non-padding base64
 * characters.  Such substrings could be keys (AWS keys and GitHub tokens are 40-character base64
 * encoded strings) or other sensitive information, but they could also be other information made
 * up of the same characters, like file paths, that should be logged.  For example, the path
 * "/MyStuff/data/input123" is composed entirely of non-padding base64 characters, but is not a
 * secret and should not be censored.
 *
 * So, when CensorHelper finds a longer base64-ish substring, it censors it if all the characters
 * are hex/decimal digits, and otherwise performs a statistical analysis to determine if the
 * substring is an encoded key versus something else.  The current implementation combines two
 * measures of the substring:
 *
 * 1. The number of adjacent characters of the same class, where "class" is either a) decimal
 *    digits, b) uppercase letters, c) lowercase letters + punctuation.
 *
 * 2. The number of "English points", where higher values represent sequences of characters more
 *    characteristic of English language sequences, currently derived from letter-triplet
 *    frequencies as calculated from an English text corpus.  Passages that resemble English text
 *    will score 1-2 points per character, and sequences of random letters will usually score
 *    negatively.
 *
 * For example, substrings like "/jkLLIiRn6+1ZwUNf1jJYCjIM/tTnp2K3Rg8PUAF6tbZ3r//Umw" score low,
 * and substrings like "/System/Library/CoreServices/SystemUIServer" score high.  Low-scoring
 * substrings are censored, and high-scoring substrings are not.
 *
 * The underlying assumption is that a key will have high information density and be efficiently
 * encoded, and thus be a random jumble of characters, whereas a path (or other structured
 * non-key data) will contain longer runs of the same character classes, and/or statistically
 * resemble English text.
 *
 * The system is not perfect, but it does pretty well.  Experiments show that it fails to censor
 * less than one in 50,000,000 randomly-generated 40-character strings composed of non-padding
 * base64 characters.  It does censor the occasional non-secret thing, but for the current use
 * case, that's ok.
 *
 * The advantage of this scheme over something like git secrets is that it censors secrets that
 * are floating by themselves, having previously been separated from adjacent identifying
 * markings or other cues.
 */

public class CensorHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CensorHelper.class);
    private static final Pattern BASE64_PATTERN = Pattern.compile("[-_a-zA-Z0-9+/]{22,}");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]*$");
    private static final int LETTER_COUNT = 26;
    private static final int LETTER_COUNT_SQUARED = LETTER_COUNT * LETTER_COUNT;
    private static final int LETTER_COUNT_CUBED = LETTER_COUNT * LETTER_COUNT * LETTER_COUNT;

    private final byte[] englishPoints;

    public CensorHelper(Map<String, Double> tripletToFrequency) {
        englishPoints = createPointArray(tripletToFrequency);
    }

    public String censor(String raw) {

        StringBuilder censored = new StringBuilder(raw);

        // Censor each longer base64-ish string if it's either all hex or is a "jumble" of characters.
        // See the class description for more information about what we consider a "jumble".
        Matcher matcher = BASE64_PATTERN.matcher(raw);
        while (matcher.find()) {

            int start = matcher.start();
            int end = matcher.end();
            String suspect = raw.substring(start, end);

            if (HEX_PATTERN.matcher(suspect).matches() || isJumbled(suspect)) {
                for (int i = start; i < end; i++) {
                    censored.setCharAt(i, 'X');
                }
            }
        }

        return censored.toString();
    }

    private boolean isJumbled(String s) {
        double length = s.length();
        double adjacentsPerChar = calculateAdjacents(s) / length;
        double englishPointsPerChar = calculateEnglishPoints(s) / length;
        double score = adjacentsPerChar * adjacentsPerChar * (1 + 2 * englishPointsPerChar);
        return score < 1.0;
    }

    private int calculateAdjacents(String s) {
        int adjacents = 0;
        for (int i = 0, n = s.length() - 1; i < n; i++) {
            char a = s.charAt(i);
            char b = s.charAt(i + 1);
            if (characterClass(a) == characterClass(b)) {
                adjacents++;
            }
        }
        return adjacents;
    }

    private int characterClass(char c) {
        if (c >= '0' && c <= '9') {
            return 0;
        }
        if (c >= 'A' && c <= 'Z') {
            return 1;
        }
        return 2;
    }

    private long calculateEnglishPoints(String s) {

        int points = 0;
        for (int i = 0, n = s.length() - 2; i < n; i++) {
            char a = s.charAt(i);
            char b = s.charAt(i + 1);
            char c = s.charAt(i + 2);
            points += calculateEnglishPoints(a, b, c);
        }
        return points;
    }

    private long calculateEnglishPoints(char a, char b, char c) {
        if (!isAlpha(a) || !isAlpha(b) || !isAlpha(c)) {
            return 0;
        }
        return englishPoints[index(a, b, c)];
    }

    private static boolean isAlpha(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    private static int index(char c) {
        if (c >= 'a' && c <= 'z') {
            return c - 'a';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        return 0;
    }

    private static int index(char a, char b, char c) {
        return index(a) + (index(b) * LETTER_COUNT) + (index(c) * LETTER_COUNT_SQUARED);
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static byte[] createPointArray(Map<String, Double> tripletToFrequency) {

        byte[] englishPoints = new byte[LETTER_COUNT_CUBED];

        // Compute the list of triplets, ordered by descending frequency.
        List<String> triplets = new ArrayList<>(tripletToFrequency.keySet());
        Collections.sort(triplets, Comparator.comparingDouble(s -> tripletToFrequency.get(s)).reversed());

        // Assign a number of points for each triplet, skewing the point distribution
        // so that triplets that frequently appear in English text will score highest,
        // and that a random triplet will score negatively, on average.
        for (int i = 0; i < triplets.size(); i++) {

            String triplet = triplets.get(i);
            if (triplet.length() != 3) {
                throw new RuntimeException("triplets must be of length 3");
            }
            char a = triplet.charAt(0);
            char b = triplet.charAt(1);
            char c = triplet.charAt(2);
            if (!isAlpha(a) || !isAlpha(b) || !isAlpha(c)) {
                throw new RuntimeException("triplets must be composed of letters");
            }

            double normalizedRank = i / (double)LETTER_COUNT_CUBED;
            int points;
            if (normalizedRank < 0.02) {
                points = 4;
            } else if (normalizedRank < 0.05) {
                points = 3;
            } else if (normalizedRank < 0.10) {
                points = 2;
            } else if (normalizedRank < 0.15) {
                points = 1;
            } else if (normalizedRank < 0.22) {
                points = 0;
            } else if (normalizedRank < 0.32) {
                points = -1;
            } else {
                points = -2;
            }

            englishPoints[index(a, b, c)] = (byte)points;
        }

        return englishPoints;
    }
}
