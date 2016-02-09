/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.client.cli;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;

import com.google.common.io.Resources;
import com.google.gson.Gson;

import io.cwl.avro.CWL;

import static org.junit.Assert.assertTrue;

/**
 *
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
    public void parseCWL() throws Exception{
        final URL resource = Resources.getResource("cwl.json");
        final CWL cwl = new CWL();
        final ImmutablePair<String, String> output = cwl.parseCWL(resource.getFile(), true);
        assertTrue(!output.getLeft().isEmpty() && output.getLeft().contains("cwlVersion"));
        assertTrue(!output.getRight().isEmpty() && output.getRight().contains("cwltool"));
    }

    @Test
    public void extractCWLTypes() throws Exception{
        final URL resource = Resources.getResource("cwl.json");
        final CWL cwl = new CWL();
        final ImmutablePair<String, String> output = cwl.parseCWL(resource.getFile(), true);
        final Map<String, String> typeMap = cwl.extractCWLTypes(output.getLeft());
        assertTrue(typeMap.size() == 3);
        assertTrue("int".equals(typeMap.get("mem_gb")));
        assertTrue("File".equals(typeMap.get("bam_input")));
    }

    @Test
    public void testFileConversions() throws Exception{
        final Object file1 = CWL.getStub("File", null);
        assertTrue(file1 instanceof Map && "fill me in".equals(((Map)file1).get("path")));
        final Object file2 = CWL.getStub("File", "foobar");
        assertTrue(file2 instanceof Map && "foobar".equals(((Map)file2).get("path")));
    }


}
