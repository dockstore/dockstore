/*
 *    Copyright 2017 OICR
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
package io.github.collaboratory.cwl.cwlrunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.Client;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class ToilWrapper implements CWLRunnerInterface {
    @Override
    public void checkForCWLDependencies() {
        final String[] s1 = { "toil-cwl-runner", "--version" };
        final ImmutablePair<String, String> pair1 = io.cwl.avro.Utilities
                .executeCommand(Joiner.on(" ").join(Arrays.asList(s1)), false, com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent());
        final String toilVersion = pair1.getValue().trim();

        final String expectedToilVersion = "3.14.0";
        if (!toilVersion.equals(expectedToilVersion)) {
            ArgumentUtility.errorMessage("toil version is " + toilVersion + " , Dockstore is tested with " + expectedToilVersion
                    + "\nOverride and run with `--script`", Client.COMMAND_ERROR);
        }
    }

    @Override
    public List<String> getExecutionCommand(String outputDir, String tmpDir, String workingDir, String cwlFile, String jsonSettings) {
        //TODO: this doesn't quite work yet, seeing "toil.batchSystems.abstractBatchSystem.InsufficientSystemResources: Requesting more disk than either physically available, or enforced by --maxDisk. Requested: 537944653824, Available: 134853001216" on trivial workflows like md5sum
        return new ArrayList<>(Arrays
            .asList("toil-cwl-runner", "--outdir", outputDir, "--tmpdir-prefix", tmpDir, "--tmp-outdir-prefix",
                workingDir, cwlFile, jsonSettings));
    }
}
