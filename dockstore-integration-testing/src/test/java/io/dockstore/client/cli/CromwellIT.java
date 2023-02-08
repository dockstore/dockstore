/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.LanguageHandlerHelper;
import io.dockstore.common.WdlBridge;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;
import wdl.draft3.parser.WdlParser;

/**
 * This tests integration with the CromWell engine and what will eventually be wdltool.
 *
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
class CromwellIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @Test
    void testWDL2Json() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        WdlBridge wdlBridge = new WdlBridge();
        try {
            String inputs = wdlBridge.getParameterFile(sourceFile.getAbsolutePath(), "/wdl.wdl");
            assertTrue(inputs.contains("three_step.cgrep.pattern"));
        } catch (WdlParser.SyntaxError ex) {
            fail("There should not be any parsing errors");
        }
    }

    @Test
    void testWDLResolver() {
        // If resolver works, this should throw no errors
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl-sanger-workflow.wdl"));
        WdlBridge wdlBridge = new WdlBridge();
        @SuppressWarnings("checkstyle:IllegalType")
        HashMap<String, String> secondaryFiles = new HashMap<>();
        secondaryFiles.put("wdl.wdl",
            """
                task ps {
                  command {
                    ps
                  }
                  output {
                    File procs = stdout()
                  }
                }

                task cgrep {
                  String pattern
                  File in_file
                  command {
                    grep '${pattern}' ${in_file} | wc -l
                  }
                  output {
                    Int count = read_int(stdout())
                  }
                }

                task wc {
                  File in_file
                  command {
                    cat ${in_file} | wc -l
                  }
                  output {
                    Int count = read_int(stdout())
                  }
                }

                workflow three_step {
                  call ps
                  call cgrep {
                    input: in_file=ps.procs
                  }
                  call wc {
                    input: in_file=ps.procs
                  }
                }
                """);
        wdlBridge.setSecondaryFiles(secondaryFiles);
        try {
            wdlBridge.validateWorkflow(sourceFile.getAbsolutePath(), "/wdl-sanger-workflow.wdl");
        } catch (WdlParser.SyntaxError ex) {
            fail("Should not fail parsing file");
        }
    }


    /**
     * This tests compatibility with Cromwell 30.2 by converting to JSON (<a href="https://github.com/dockstore/dockstore/issues/1211">...</a>)
     */
    @Test
    void testWDL2JsonIssue() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("hello_world.wdl"));
        WdlBridge wdlBridge = new WdlBridge();
        try {
            String inputs = wdlBridge.getParameterFile(sourceFile.getAbsolutePath(), "/hello_world.wdl");
            assertTrue(inputs.contains("wf.hello_world.hello_input"));
        } catch (WdlParser.SyntaxError ex) {
            fail("Should properly parse document");
        }
    }

    /**
     * Tests that we can generate a DAG for <a href="https://staging.dockstore.org/workflows/github.com/HumanCellAtlas/skylab/Snap-ATAC:gl_576?tab=info">
     *     </a>
     */
    @Test
    void testSnapAtacDag() {
        final File file = new File(ResourceHelpers.resourceFilePath("snap_atac.wdl"));
        final WdlBridge wdlBridge = new WdlBridge();
        final Map<String, List<String>> callsToDependencies = wdlBridge.getCallsToDependencies(file.getAbsolutePath(), "snap_atac.wdl");
        assertEquals(5, callsToDependencies.size());
    }

    @Test
    void testPathResolver() {
        assertEquals("/module00a/Module00a.wdl", LanguageHandlerHelper
                .unsafeConvertRelativePathToAbsolutePath("/GATKSVPipelineClinical.wdl", "module00a/Module00a.wdl"));
        assertEquals("/a/importA.wdl", LanguageHandlerHelper
                .unsafeConvertRelativePathToAbsolutePath("/parent/parent.wdl", "../a/importA.wdl"));
    }
}
