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

package io.dockstore.client.cli;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import io.cwl.avro.CWL;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author dyuen
 */
public class CWLClientTest {

    @Test
    public void serializeToJson() throws Exception {
        final URL resource = Resources.getResource("cwl.json");
        final String cwlJson = Resources.toString(resource, StandardCharsets.UTF_8);

        final CWL cwl = new CWL();

        final Gson gson = CWL.getTypeSafeCWLToolDocument();
        final Map<String, Object> runJson = cwl.extractRunJson(cwlJson);
        final String s = gson.toJson(runJson);
        assertTrue(s.length() > 10);
    }

    @Test
    public void parseCWL() throws Exception {
        final URL resource = Resources.getResource("cwl.json");
        final CWL cwl = new CWL();
        final ImmutablePair<String, String> output = cwl.parseCWL(resource.getFile());
        assertTrue(!output.getLeft().isEmpty() && output.getLeft().contains("cwlVersion"));
        assertTrue(!output.getRight().isEmpty() && output.getRight().contains("cwltool"));
    }

    @Test
    public void extractCWLTypes() throws Exception {
        final URL resource = Resources.getResource("cwl.json");
        final CWL cwl = new CWL();
        final ImmutablePair<String, String> output = cwl.parseCWL(resource.getFile());
        final Map<String, String> typeMap = cwl.extractCWLTypes(output.getLeft());
        assertTrue(typeMap.size() == 3);
        assertTrue("int".equals(typeMap.get("mem_gb")));
        assertTrue("File".equals(typeMap.get("bam_input")));
    }

    @Test
    public void testFileConversions() throws Exception {
        final Object file1 = CWL.getStub("File", null);
        assertTrue(file1 instanceof Map && "/tmp/fill_me_in.txt".equals(((Map)file1).get("path")));
        final Object file2 = CWL.getStub("File", "foobar");
        assertTrue(file2 instanceof Map && "foobar".equals(((Map)file2).get("path")));
    }

}
