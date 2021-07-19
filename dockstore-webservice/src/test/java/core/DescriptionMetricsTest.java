package core;

import io.dockstore.webservice.core.DescriptionMetrics;
import org.junit.Assert;
import org.junit.Test;

public class DescriptionMetricsTest {

    @Test
    public void testEntropyAndLengthCalculation() {
        final String testString1 = "abcdef";
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(testString1);
        Assert.assertEquals("Incorrect description length", 6, descriptionMetrics.getDescriptionLength());
        Assert.assertEquals("Incorrect entropy", 6, descriptionMetrics.getCalculatedEntropy());

        final String testString2 = "abcdef1234";
        descriptionMetrics = new DescriptionMetrics(testString2);
        Assert.assertEquals("Incorrect description length", 10, descriptionMetrics.getDescriptionLength());
        Assert.assertEquals("Incorrect entropy", 10, descriptionMetrics.getCalculatedEntropy());

        final String testString3 = "aaaaabbbbbccccc\ndddddeeeeefffff";
        descriptionMetrics = new DescriptionMetrics(testString3);
        Assert.assertEquals("Incorrect description length", 31, descriptionMetrics.getDescriptionLength());
        Assert.assertEquals("Incorrect entropy", 7, descriptionMetrics.getCalculatedEntropy());
    }

    @Test
    public void testWordCountCalculation() {
        final String testString1 = "one two three four, I declare S#P$E*C)I*A!L characters";
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(testString1);
        Assert.assertEquals("Incorrect word count", 8, descriptionMetrics.getCalculatedWordCount());

        final String testString2 = "      leading  spaces      ending             spaces      ";
        descriptionMetrics = new DescriptionMetrics(testString2);
        Assert.assertEquals("Incorrect word count", 4, descriptionMetrics.getCalculatedWordCount());

        final String testString3 = "strange_delimiters_should_not_be_counted_as_multiple_words";
        descriptionMetrics = new DescriptionMetrics(testString3);
        Assert.assertEquals("Incorrect word count", 1, descriptionMetrics.getCalculatedWordCount());

        final String testString4 = "       ";
        descriptionMetrics = new DescriptionMetrics(testString4);
        Assert.assertEquals("A description of only spaces has no words", 0, descriptionMetrics.getCalculatedWordCount());
    }

    @Test
    public void testNullDescription() {
        DescriptionMetrics descriptionMetrics = new DescriptionMetrics(null);
        Assert.assertEquals("A null description has no words", 0, descriptionMetrics.getCalculatedWordCount());
        Assert.assertEquals("A null description has no length", 0, descriptionMetrics.getDescriptionLength());
        Assert.assertEquals("A null description has no entropy", 0, descriptionMetrics.getCalculatedEntropy());
    }
}
