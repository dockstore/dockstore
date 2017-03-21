package io.dockstore.webservice.helpers;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Test;

import java.io.File;

import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;
import static org.junit.Assert.*;

/**
 * Created by kcao on 21/03/17.
 */
public class JsonLdRetrieverTest {
    private void getSchema(String cwl, String json) throws Exception {
        Tool tool = new Tool();
        Tag tag = new Tag();
        SourceFile file = new SourceFile();

        File cwlFile = new File(ResourceHelpers.resourceFilePath(cwl));
        String cwlContent = Files.toString(cwlFile, Charsets.UTF_8);

        file.setContent(cwlContent);
        file.setType(DOCKSTORE_CWL);
        tag.addSourceFile(file);
        tag.setReference("master");
        tool.addTag(tag);
        tool.setDefaultVersion("master");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String schemaJson = gson.toJson(JsonLdRetriever.getSchema(tool));

        File expected = new File(ResourceHelpers.resourceFilePath(json));
        String expectedJson = Files.toString(expected, Charsets.UTF_8);

        assertEquals(schemaJson, expectedJson);
    }

    @Test
    public void getSchema_hasSchema() throws Exception {
        getSchema("schema.cwl", "schema.json");
    }

    @Test
    public void getSchema_noSchema() throws Exception {
        getSchema("noSchema.cwl", "noSchema.json");
    }
}