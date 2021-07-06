package io.dockstore.webservice.core;

public class DescriptionMetrics {

    private final long descriptionLength;

    private final long entropy;

    private final long wordCount;

    /**
     * Calculates various description metrics based on the input description's content.
     * @param descriptionContent String content of a description
     */
    public DescriptionMetrics(String descriptionContent) {
        if (descriptionContent == null) {
            this.descriptionLength = 0;
            this.entropy = 0;
            this.wordCount = 0;
        } else {
            this.descriptionLength = descriptionContent.length();
            this.entropy = this.calculateEntropy(descriptionContent);
            this.wordCount = this.calculateWordCount(descriptionContent);
        }
    }

    /**
     * Calculates the entropy of the input description. Entropy, in this case, is represented as the
     * number of distinct characters within the description.
     * @param descriptionContent String content of a description
     */
    public long calculateEntropy(String descriptionContent) {
        return descriptionContent.chars().distinct().count();
    }

    /**
     * Calculates word count of the input description. The delimiter between words is any number of spaces >= 1.
     * Leading and trailing spaces are trimmed.
     * @param descriptionContent String content of a description
     */
    public long calculateWordCount(String descriptionContent) {

        // trim leading and trailing spaces
        String trimmed = descriptionContent.trim();
        if (trimmed.length() == 0) {
            return 0;
        }

        // split into a String[] by spaces and return the length. Split by any sequences of spaces with length >= 1.
        return trimmed.split("\\s+").length;
    }

    public long getDescriptionLength() {
        return this.descriptionLength;
    }

    public long getCalculatedEntropy() {
        return this.entropy;
    }

    public long getCalculatedWordCount() {
        return this.wordCount;
    }
}
