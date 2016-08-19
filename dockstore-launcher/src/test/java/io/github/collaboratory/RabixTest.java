/*
 *    Copyright 2016 OICR
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
package io.github.collaboratory;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.rabix.backend.local.BackendCommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test out integration of Rabix jar
 */
public class RabixTest {

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testRabixCall() throws IOException {
        File cwlFile = FileUtils.getFile("src", "test", "resources", "dna2protein.cwl.json");
        File jobFile = FileUtils.getFile("src", "test", "resources", "translate.cwl.json");
        File configDir = FileUtils.getFile("src", "test", "resources");

        exit.expectSystemExitWithStatus(0);
        // need to create a dummy docker config file for spotify client
        Path path = Paths.get(System.getProperty("user.home"), ".dockercfg");
        if (!path.toFile().exists()){
            path.toFile().createNewFile();
        }

        BackendCommandLine.main(new String[]{"-c", configDir.getAbsolutePath(), cwlFile.getAbsolutePath(), jobFile.getAbsolutePath()});
    }
}
