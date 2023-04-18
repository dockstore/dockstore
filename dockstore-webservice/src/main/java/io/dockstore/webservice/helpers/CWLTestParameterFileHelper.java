/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Helper for converting a CWL test parameter file into Java objects.
 *
 * Currently only looks for parameters of the class file and ignores other types.
 *
 * A file parameter can take these forms, in both cases the <code>secondaryFiles</code> is optional.
 *
 * <ul>
 *     <li><pre>
 *     "bam_cram_file": {
 *       "class": "File",
 *       "path": "gs://topmed_workflow_testing/topmed_variant_caller/input_files/NWD176325.recab.cram",
 *       "secondaryFiles": [
 *         {
 *           "class": "File",
 *         "path": "gs://topmed_workflow_testing/topmed_variant_caller/input_files/NWD176325.recab.cram.crai"
 *         }
 *       ]
 *     }
 *     </pre></li>
 *     <li><pre>
 *     "reads": [
 *     {
 *       "path": "https://dcc.icgc.org/api/v1/download?fn=/PCAWG/reference_data/data_for_testing/hg19.chr22.5x.normal.bam",
 *       "basename": "hg19.chr22.5x.normal.bam",
 *       "class": "File",
 *       "secondaryFiles": [
 *         "path": "https://dcc.icgc.org/api/v1/download?fn=/PCAWG/reference_data/data_for_testing/hg19.chr22.5x.normal.bam.bai"
 *         "class": "File"
 *       ]
 *     },
 *     {
 *       "path": "https://dcc.icgc.org/api/v1/download?fn=/PCAWG/reference_data/data_for_testing/hg19.chr22.5x.normal2.bam",
 *       "basename": "hg19.chr22.5x.normal2.bam",
 *       "class": "File"
 *     }
 *   ]
 *     </pre>
 *     </li>
 * </ul>
 */
public class CWLTestParameterFileHelper {

    private static final String PATH = "path";
    private static final String LOCATION = "location";
    private static final String CLASS = "class";
    private static final String FILE = "File";
    private static final String SECONDARY_FILES = "secondaryFiles";

    /**
     * Gets the inputs from a test parameter file that are of the type file
     * @param parameterFile jsonified test parameter file
     * @return
     */
    public List<FileInput> fileInputs(JSONObject parameterFile) {
        final List<FileInput> fileInputs = new ArrayList<>();
        for (Iterator<String> it = parameterFile.keys(); it.hasNext();) {
            final String paramName = it.next();
            final Object paramValue = parameterFile.get(paramName);
            if (isFile(paramValue)) {
                final JSONObject jsonObject = (JSONObject) paramValue;
                fileInputs.add(new FileInput(paramName, getPaths(jsonObject)));
            } else if (paramValue instanceof JSONArray jsonArray) {
                fileInputs.add(new FileInput(paramName, getFilePathsFromArray(jsonArray)));
            }
        }
        return fileInputs;
    }

    private List<String> getPaths(JSONObject fileJsonObject) {
        String path = fileJsonObject.optString(PATH, fileJsonObject.optString(LOCATION, null));
        if (path != null) {
            final List<String> secondaryFiles = getSecondaryFiles(fileJsonObject);
            return Stream.concat(Stream.of(path), secondaryFiles.stream()).toList();
        }
        return List.of();
    }

    private List<String> getSecondaryFiles(JSONObject jsonObject) {
        return getFilePathsFromArray(jsonObject.optJSONArray(SECONDARY_FILES));
    }

    private List<String> getFilePathsFromArray(Object obj) {
        if (obj instanceof JSONArray jsonArray) {
            return StreamSupport.stream(jsonArray.spliterator(), false)
                .filter(this::isFile)
                .map(JSONObject.class::cast)
                .map(this::getPaths)
                .flatMap(Collection::stream)
                .toList();
        }
        return List.of();
    }

    private boolean isFile(Object obj) {
        if (obj instanceof JSONObject jsonObject) {
            final String clazz = jsonObject.optString(CLASS);
            final String path = jsonObject.optString(PATH);
            return FILE.equals(clazz) && path != null;
        }
        return false;
    }

    /**
     * Represents an input of type File in a test parameter file. In the test parameter file,
     * a file input parameter can have one 1 to n paths. In addition, each of those paths can in
     * turn have 0 to n secondary file paths. The "main" path and secondary paths are combined into
     * one list in this structure.
     *
     * @param parameterName
     * @param paths
     */
    public record FileInput(String parameterName, List<String> paths) {}
}

