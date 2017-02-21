package io.dockstore.client.cli.nested;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import static org.junit.Assert.assertTrue;

/**
 * Created by kcao on 17/02/17.
 */
public class InvalidOutputsTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    public static final AbstractEntryClient entryClient = new ToolClient(null, false);
    public static File cwlTestFile;
    public static StringBuilder stringBuilder = new StringBuilder("cwlVersion: cwl:draft-3\n");

    @BeforeClass
    public static void baseCWL() {
        cwlTestFile = new File(System.getProperty("user.home") + File.separator + "invalidCWLOutputs.cwl");
        stringBuilder.append("class: CommandLineTool\n");
        stringBuilder.append("baseCommand: echo\n");
        stringBuilder.append("inputs:\n");
        stringBuilder.append("- id: message\n");
        stringBuilder.append("type: string\n");
        stringBuilder.append("inputBinding:\n");
        stringBuilder.append("position:1\n");
    }

    @Test
    public void checkCWL_EmptyOutputList() throws Exception {
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + "outputs: []\n", "UTF-8");
        assertTrue(entryClient.checkCWL(cwlTestFile));
    }

    @Test
    public void checkCWL_InvalidOutputList() throws Exception {
        exit.expectSystemExitWithStatus(4);
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + "outputs: \n", "UTF-8");
        entryClient.checkCWL(cwlTestFile);
    }

    @Test
    public void checkCWL_IDWithNonWordOutputList() throws Exception {
        StringBuilder idSection = new StringBuilder("outputs:\n");
        idSection.append(" - id: \"#example_out\"\n");
        idSection.append("    type: File\n");
        idSection.append("    outputBinding:\n");
        idSection.append("      glob: hello.txt");
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + idSection.toString(), "UTF-8");
        assertTrue(entryClient.checkCWL(cwlTestFile));
    }

    @Test
    public void checkCWL_IDWordCharsOutputList() throws Exception {
        StringBuilder idSection = new StringBuilder("outputs:\n");
        idSection.append(" - id: example_out\n");
        idSection.append("    type: File\n");
        idSection.append("    outputBinding:\n");
        idSection.append("      glob: hello.txt");
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + idSection.toString(), "UTF-8");
        assertTrue(entryClient.checkCWL(cwlTestFile));
    }

    @Test
    public void checkCWL_InvalidID() throws Exception {
        exit.expectSystemExitWithStatus(4);
        StringBuilder idSection = new StringBuilder("outputs:\n");
        idSection.append("   id: example_out\n");
        idSection.append("    type: File\n");
        idSection.append("    outputBinding:\n");
        idSection.append("      glob: hello.txt");
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + idSection.toString(), "UTF-8");
        assertTrue(entryClient.checkCWL(cwlTestFile));
    }

    @After
    public void cleanFile() {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(cwlTestFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        writer.print("");
        writer.close();
    }

    @AfterClass
    public static void removeFile() {
        cwlTestFile.delete();
    }
}