package core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.webservice.core.DescriptionMetrics;
import org.junit.jupiter.api.Test;

class DescriptionMetricsTest {

    @Test
    void testEntropyAndLengthCalculation() {
        final String testString1 = "abcdef";
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(testString1);
        assertEquals(6, descriptionMetrics.getDescriptionLength(), "Incorrect description length");
        assertEquals(6, descriptionMetrics.getCalculatedEntropy(), "Incorrect entropy");

        final String testString2 = "abcdef1234";
        descriptionMetrics = new DescriptionMetrics(testString2);
        assertEquals(10, descriptionMetrics.getDescriptionLength(), "Incorrect description length");
        assertEquals(10, descriptionMetrics.getCalculatedEntropy(), "Incorrect entropy");

        final String testString3 = "aaaaabbbbbccccc\ndddddeeeeefffff";
        descriptionMetrics = new DescriptionMetrics(testString3);
        assertEquals(31, descriptionMetrics.getDescriptionLength(), "Incorrect description length");
        assertEquals(7, descriptionMetrics.getCalculatedEntropy(), "Incorrect entropy");
    }

    @Test
    void testWordCountCalculation() {
        final String testString1 = "one two three four, I declare S#P$E*C)I*A!L characters";
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(testString1);
        assertEquals(8, descriptionMetrics.getCalculatedWordCount(), "Incorrect word count");

        final String testString2 = "      leading  spaces      ending             spaces      ";
        descriptionMetrics = new DescriptionMetrics(testString2);
        assertEquals(4, descriptionMetrics.getCalculatedWordCount(), "Incorrect word count");

        final String testString3 = "strange_delimiters_should_not_be_counted_as_multiple_words";
        descriptionMetrics = new DescriptionMetrics(testString3);
        assertEquals(1, descriptionMetrics.getCalculatedWordCount(), "Incorrect word count");

        final String testString4 = "       ";
        descriptionMetrics = new DescriptionMetrics(testString4);
        assertEquals(0, descriptionMetrics.getCalculatedWordCount(), "A description of only spaces has no words");
    }

    @Test
    void testNullDescription() {
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(null);
        assertEquals(0, descriptionMetrics.getCalculatedWordCount(), "A null description has no words");
        assertEquals(0, descriptionMetrics.getDescriptionLength(), "A null description has no length");
        assertEquals(0, descriptionMetrics.getCalculatedEntropy(), "A null description has no entropy");
    }
}
