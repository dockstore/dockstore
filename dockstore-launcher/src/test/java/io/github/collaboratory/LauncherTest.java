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

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonClientException;

import static org.junit.Assert.assertTrue;

/**
 * @author dyuen
 */
public class LauncherTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testCWL() throws Exception {
        File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.ini");
        File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
        File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job-pre.json");

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expect(AmazonClientException.class);
            expectedEx.expectMessage("Unable to load AWS credentials from any provider in the chain");
        }
        final LauncherCWL launcherCWL = new LauncherCWL(new String[] { "--config", iniFile.getAbsolutePath(), "--descriptor",
                cwlFile.getAbsolutePath(), "--job", jobFile.getAbsolutePath() });
        launcherCWL.run();
    }
        
    @Test
    public void testCWLProgrammatic() throws Exception {
        File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.ini");
        File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
        File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job-pre.json");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expect(AmazonClientException.class);
            expectedEx.expectMessage("Unable to load AWS credentials from any provider in the chain");
        }
        final LauncherCWL launcherCWL = new LauncherCWL(iniFile.getAbsolutePath(), cwlFile.getAbsolutePath(), jobFile.getAbsolutePath(), stdout, stderr);
        launcherCWL.run();

        assertTrue(!stdout.toString().isEmpty());
        assertTrue(!stderr.toString().isEmpty());
    }
}
