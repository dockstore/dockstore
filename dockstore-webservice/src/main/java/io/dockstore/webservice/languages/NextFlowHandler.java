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
package io.dockstore.webservice.languages;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.http.HttpStatus;

/**
 * This class will eventually handle support for NextFlow
 */
public class NextFlowHandler implements LanguageHandlerInterface {

    @Override
    public Entry parseWorkflowContent(Entry entry, String content, Set<SourceFile> sourceFiles) {
        // this is where we can look for things like NextFlow config files or maybe a future Dockstore.yml
        ConfigSlurper slurper = new ConfigSlurper();
        ConfigObject parse = slurper.parse(content);
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        if (manifest.containsKey("description")) {
            entry.setDescription((String)manifest.get("description"));
        }
        if (manifest.containsKey("author")) {
            entry.setAuthor((String)manifest.get("author"));
        }

        return entry;
    }

    @Override
    public boolean isValidWorkflow(String content) {
        return content.contains("manifest");
    }

    @Override
    public Map<String, SourceFile> processImports(String content, Version version, SourceCodeRepoInterface sourceCodeRepoInterface) {
        ConfigSlurper slurper = new ConfigSlurper();
        ConfigObject parse = slurper.parse(content);
        Map<String, SourceFile> imports = new HashMap<>();

        // add the NextFlow scripts
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        String mainScriptPath = "main.nf";
        if (manifest.containsKey("mainScript")) {
            mainScriptPath = (String)manifest.get("mainScript");
        }
        SourceFile sourceFile = sourceCodeRepoInterface.readFile(version, SourceFile.FileType.NEXTFLOW, mainScriptPath);
        imports.put(mainScriptPath, sourceFile);

        // TODO: does NextFlow have imports beyond the main script file linked to from nextflow.config?

        return imports;
    }

    @Override
    public String getContent(String mainDescName, String mainDescriptor, Map<String, String> secondaryDescContent, Type type, ToolDAO dao) {
        // pending some better bindings to understand nextflow workflows
        throw new CustomWebApplicationException("Dockstore currently does not parse NextFlow workflows into DAG form", HttpStatus.SC_SERVICE_UNAVAILABLE);
    }
}
