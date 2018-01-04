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
package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.base.Optional;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class CWLParseTesting {


    @Test
    public void testOldMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.cwl");
        TestingInterface sInterface = new TestingInterface();
        Entry entry = sInterface.parseContent(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8));
        Assert.assertTrue("incorrect author", entry.getAuthor().equals("Keiran Raine"));
        Assert.assertTrue("incorrect email", entry.getEmail().equals("keiranmraine@gmail.com"));
    }

    @Test
    public void testNewMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.cwl");
        TestingInterface sInterface = new TestingInterface();
        Entry entry = sInterface.parseContent(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8));
        Assert.assertTrue("incorrect author", entry.getAuthor().equals("Denis Yuen"));
        Assert.assertTrue("incorrect email", entry.getEmail().equals("dyuen@oicr.on.ca"));
    }

    @Test
    public void testCombinedMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example3.cwl");
        TestingInterface sInterface = new TestingInterface();
        Entry entry = sInterface.parseContent(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8));
        Assert.assertTrue("incorrect author", entry.getAuthor().equals("Denis Yuen"));
        Assert.assertTrue("incorrect email", entry.getEmail().equals("dyuen@oicr.on.ca"));
    }

    public class TestingInterface extends SourceCodeRepoInterface {

        public Entry parseContent(String content){
            Tool tool = new Tool();
            return super.parseCWLContent(tool, content);
        }

        @Override
        public String readFile(String fileName, String reference) {
            return null;
        }

        @Override
        public String getOrganizationEmail() {
            return null;
        }

        @Override
        public Map<String, String> getWorkflowGitUrl2RepositoryId() {
            return null;
        }

        @Override
        public boolean checkSourceCodeValidity() {
            return false;
        }

        @Override
        public Workflow initializeWorkflow(String repositoryId) {
            return null;
        }

        @Override
        public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults) {
            return null;
        }

        @Override
        public String getRepositoryId(Entry entry) {
            return null;
        }

        @Override
        public String getMainBranch(Entry entry, String repositoryId) {
            return null;
        }

        @Override
        public String getFileContents(String filePath, String branch, String repositoryId) {
            return null;
        }
    }
}
