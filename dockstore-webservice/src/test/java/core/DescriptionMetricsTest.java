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
}
