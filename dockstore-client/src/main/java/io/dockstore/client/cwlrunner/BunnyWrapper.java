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
package io.dockstore.client.cwlrunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.cwl.avro.CWL;

public class BunnyWrapper implements CWLRunnerInterface {
    @Override
    public void checkForCWLDependencies() {

    }

    @Override
    public List<String> getExecutionCommand(String outputDir, String tmpDir, String workingDir, String cwlFile, String jsonSettings) {
        Path path = Paths.get(System.getProperty("user.home"), CWL.RABIX_EXEC_LOCATION);
        return new ArrayList<>(Arrays.asList(path.toAbsolutePath().toString(), "--basedir", workingDir, cwlFile, jsonSettings));
    }

}
