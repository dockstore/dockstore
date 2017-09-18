package io.github.collaboratory;

import java.util.stream.IntStream;

import com.google.gson.Gson;
import io.cwl.avro.CWL;
import io.cwl.avro.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * @author gluu
 * @since 18/09/17
 */
public class SecondaryFilesUtilityTest {

    private static final String imageDescriptorPath = FileUtils
            .getFile("src", "test", "resources", "gdc/cwl/workflows/dnaseq/transform.cwl").getAbsolutePath();
    private static final CWL cwlUtil = new CWL();
    private static final String imageDescriptorContent = cwlUtil.parseCWL(imageDescriptorPath).getLeft();
    private static final Gson gson = CWL.getTypeSafeCWLToolDocument();
    private static final Object cwlObject = gson.fromJson(imageDescriptorContent, Workflow.class);

    @Test
    public void modifyWorkflowToIncludeToolSecondaryFiles() throws Exception {
        IntStream.range(0, 5).forEach(i -> modifyWorkflow());
    }

    private void modifyWorkflow() {
        SecondaryFilesUtility secondaryFilesUtility = new SecondaryFilesUtility(cwlUtil, gson);
        secondaryFilesUtility.modifyWorkflowToIncludeToolSecondaryFiles((Workflow)cwlObject);
    }
}
