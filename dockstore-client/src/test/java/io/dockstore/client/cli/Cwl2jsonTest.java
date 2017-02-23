package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.ToolClient;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kcao on 21/02/17.
 */
public class Cwl2jsonTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    public static final AbstractEntryClient entryClient = new ToolClient(null, false);
    public static File cwlTestFile;
    public static StringBuilder stringBuilder = new StringBuilder("cwlVersion: cwl:draft-3\n");

    @BeforeClass
    public static void baseCWL() {
        cwlTestFile = new File(System.getProperty("user.home") + File.separator + "invalidCwlTest.cwl");

        stringBuilder.append("cwlVersion: v1.0\n");
        stringBuilder.append("class: CommandLineTool\n");
        stringBuilder.append("baseCommand: echo\n");
        stringBuilder.append("inputs:\n");
        stringBuilder.append("  - id: message\n");
        stringBuilder.append("    type: string\n");
        stringBuilder.append("    inputBinding:\n");
        stringBuilder.append("      position: 1\n");
    }

    @Test
    public void cwl2json_MissingOutput() throws Exception {
        exit.expectSystemExitWithStatus(6);
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString(), "UTF-8");

        List arguments = new ArrayList<>();
        arguments.add("--cwl");
        arguments.add(cwlTestFile.getAbsolutePath());

        Whitebox.invokeMethod(entryClient, "cwl2json", arguments, true);
    }

    @Test
    public void cwl2json_valid() throws Exception {
        FileUtils.writeStringToFile(cwlTestFile, stringBuilder.toString() + "outputs: []", "UTF-8");

        List arguments = new ArrayList<>();
        arguments.add("--cwl");
        arguments.add(cwlTestFile.getAbsolutePath());

        Whitebox.invokeMethod(entryClient, "cwl2json", arguments, true);
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
