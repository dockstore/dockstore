package io.github.collaboratory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import io.cwl.avro.CWL;
import io.cwl.avro.InputParameter;
import io.cwl.avro.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

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


    @Test
    public void modifyWorkflowToIncludeToolSecondaryFiles() throws Exception {
        IntStream.range(0, 5).forEach(i -> modifyWorkflow());
    }

    private void modifyWorkflow() {
        Workflow workflow = gson.fromJson(imageDescriptorContent, Workflow.class);
        SecondaryFilesUtility secondaryFilesUtility = new SecondaryFilesUtility(cwlUtil, gson);
        secondaryFilesUtility.modifyWorkflowToIncludeToolSecondaryFiles(workflow);
        List<Object> inputParameters = new ArrayList<>();
        workflow.getInputs().forEach(input -> {
            Object secondaryFiles = input.getSecondaryFiles();
            if (secondaryFiles != null) {
                inputParameters.add(secondaryFiles);
            }
        });
        assertTrue(inputParameters.size() == 1);
        ArrayList inputParameterArray = (ArrayList)inputParameters.get(0);
        assertTrue(inputParameterArray.size() == 5);


    }
}
