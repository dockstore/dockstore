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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;

public class ElasticManagerIT {
    private static ElasticManager manager;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @BeforeClass
    public static void setupManager() {
        DockstoreWebserviceConfiguration config = new DockstoreWebserviceConfiguration();
        config.getEsConfiguration().setHostname("localhost");
        config.getEsConfiguration().setPort(9200);
        ElasticManagerIT.manager = new ElasticManager();
        ElasticManager.setConfig(config);
    }

    @Test
    public void addAnEntry() throws IOException {
        Tool tool = new Tool();
        Tag tag = new Tag();
        SourceFile file = new SourceFile();

        File cwlFile = new File(ResourceHelpers.resourceFilePath("schema.cwl"));

        String cwlContent = Files.asCharSource(cwlFile, Charsets.UTF_8).read();

        file.setPath("dummypath");
        file.setAbsolutePath("/dummypath");
        file.setContent(cwlContent);
        file.setType(DOCKSTORE_CWL);
        tag.addSourceFile(file);
        tag.setReference("master");
        tool.addTag(tag);
        tool.setDefaultVersion("master");
        tool.setIsPublished(true);

        manager.handleIndexUpdate(tool, ElasticMode.UPDATE);

        manager.bulkUpsert(Collections.singletonList(tool));

        //TODO: should extend this by checking that elastic search holds the content we expect
        Assert.assertTrue("could not talk to elastic search", !systemOutRule.getLog().contains("Connection refused"));
    }

    @Test
    public void filterCheckerWorkflows() {
        Workflow workflow = new Workflow();
        workflow.setIsChecker(true);
        Tool tool = new Tool();
        List<Entry> entries = manager.filterCheckerWorkflows(Arrays.asList(workflow, tool));
        Assert.assertEquals("There should've been 1 entry without checker workflow", 1, entries.size());
        entries.forEach(entry -> Assert.assertFalse("There should be no workflow", entry instanceof Workflow));
    }


}
