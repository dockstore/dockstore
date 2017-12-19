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

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;

/**
 * This class will eventually handle support for NextFlow
 */
public class NextFlowHandler implements LanguageHandlerInterface {
    private final SourceCodeRepoInterface sourceCodeRepoInterface;

    NextFlowHandler(SourceCodeRepoInterface sourceCodeRepoInterface) {
        this.sourceCodeRepoInterface = sourceCodeRepoInterface;
    }

    @Override
    public Entry parseWorkflowContent(Entry entry, String content) {
        // TODO
        return entry;
    }

    @Override
    public boolean isValidWorkflow(String content) {
        // TODO
        return true;
    }

    @Override
    public Map<String, SourceFile> processImports(String content, Version version) {
        // this is where we can look for things like NextFlow config files or maybe a future Dockstore.yml
        ConfigSlurper slurper = new ConfigSlurper();
        ConfigObject parse = slurper.parse(content);
        Map<String, SourceFile> imports = new HashMap<>();
        return imports;
    }
}
