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

public class CensorHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CensorHelper.class);
    private static final Pattern BASE64_PATTERN = Pattern.compile("[-_a-zA-Z0-9+/]{24,}");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]*$");
    private static final int LETTER_COUNT = 26;
    private static final int LETTER_COUNT_SQUARED = LETTER_COUNT * LETTER_COUNT;
    private static final int LETTER_COUNT_CUBED = LETTER_COUNT * LETTER_COUNT * LETTER_COUNT;

    private byte[] englishPoints;

    public CensorHelper(Map<String, Double> tripletToFrequency) {
        englishPoints = createPointArray(tripletToFrequency);
    }

    public String censor(String raw) {

        StringBuilder censored = new StringBuilder(raw);

        Matcher matcher = BASE64_PATTERN.matcher(raw);
        while (matcher.find()) {

            int start = matcher.start();
            int end = matcher.end();
            String suspect = raw.substring(start, end);

            if (HEX_PATTERN.matcher(suspect).matches() || isScrambled(suspect)) {
                for (int i = start; i < end; i++) {
                    censored.setCharAt(i, 'X');
                }
            }
        }

        return censored.toString();
    }

    public boolean isScrambled(String s) {
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
        return index(a) + index(b) * LETTER_COUNT + index(c) * LETTER_COUNT_SQUARED;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static byte[] createPointArray(Map<String, Double> tripletToFrequency) {

        byte[] englishPoints = new byte[LETTER_COUNT_CUBED];

        List<String> triplets = new ArrayList<>(tripletToFrequency.keySet());
        Collections.sort(triplets, Comparator.comparingDouble(s -> tripletToFrequency.get(s)).reversed());

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
