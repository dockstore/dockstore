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
import java.util.List;
import java.util.Map;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Strings;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will eventually handle support for understanding CWL
 */
public class CWLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(CWLHandler.class);
    private final SourceCodeRepoInterface sourceCodeRepoInterface;

    CWLHandler(SourceCodeRepoInterface sourceCodeRepoInterface) {
        this.sourceCodeRepoInterface = sourceCodeRepoInterface;
    }

    @Override
    public Entry parseWorkflowContent(Entry entry, String content) {
        // parse the collab.cwl file to get important metadata
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map)object;

                String description = (String)map.get("description");
                // changed for CWL 1.0
                if (map.containsKey("doc")) {
                    description = (String)map.get("doc");
                }
                if (description != null) {
                    entry.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                String dctKey = "dct:creator";
                String schemaKey = "s:author";
                if (map.containsKey(schemaKey)) {
                    processAuthor(entry, map, schemaKey, "s:name", "s:email", "Author not found!");
                } else if (map.containsKey(dctKey)) {
                    processAuthor(entry, map, dctKey, "foaf:name", "foaf:mbox", "Creator not found!");
                }

                LOG.info("Repository has Dockstore.cwl");
            } catch (YamlException ex) {
                LOG.info("CWL file is malformed " + ex.getCause().toString());
                throw new CustomWebApplicationException("Could not parse yaml: " + ex.getCause().toString(), HttpStatus.SC_BAD_REQUEST);
            }
        }
        return entry;
    }

    /**
     * Look at the map of metadata and populate entry with an author and email
     * @param entry
     * @param map
     * @param dctKey
     * @param authorKey
     * @param emailKey
     * @param errorMessage
     */
    private void processAuthor(Entry entry, Map map, String dctKey, String authorKey, String emailKey, String errorMessage) {
        Object o = map.get(dctKey);
        if (o instanceof List) {
            o = ((List)o).get(0);
        }
        map = (Map)o;
        if (map != null) {
            String author = (String)map.get(authorKey);
            entry.setAuthor(author);
            String email = (String)map.get(emailKey);
            if (!Strings.isNullOrEmpty(email)) {
                entry.setEmail(email.replaceFirst("^mailto:", ""));
            }
        } else {
            LOG.info(errorMessage);
        }
    }

    @Override
    public boolean isValidWorkflow(String content) {
        return content.contains("class: Workflow");
    }

    @Override
    public Map<String, SourceFile> processImports(String content, Version version) {
        Map<String, SourceFile> imports = new HashMap<>();
        YamlReader reader = new YamlReader(content);
        try {
            Map<String, ?> map = reader.read(Map.class);
            handleMap(version, imports, map);
        } catch (YamlException e) {
            SourceCodeRepoInterface.LOG.error("Could not process content from workflow as yaml");
        }

        Map<String, SourceFile> recursiveImports = new HashMap<>();
        for (SourceFile file : imports.values()) {
            final Map<String, SourceFile> sourceFiles = processImports(file.getContent(), version);
            recursiveImports.putAll(sourceFiles);
        }
        recursiveImports.putAll(imports);
        return recursiveImports;
    }

    private void handleMap(Version version, Map<String, SourceFile> imports,
        Map<String, ?> map) {
        for (Map.Entry<String, ?> e : map.entrySet()) {
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import") || e.getKey().equalsIgnoreCase("$include") || e.getKey().equalsIgnoreCase("import")
                || e.getKey().equalsIgnoreCase("include")) {
                // handle imports and includes
                if (mapValue instanceof String) {
                    handleImport(version, imports, (String)mapValue);
                }
            } else if (e.getKey().equalsIgnoreCase("run")) {
                // for workflows, bare files may be referenced. See https://github.com/ga4gh/dockstore/issues/208
                //ex:
                //  run: {import: revtool.cwl}
                //  run: revtool.cwl
                if (mapValue instanceof String) {
                    handleImport(version, imports, (String)mapValue);
                } else if (mapValue instanceof Map) {
                    // this handles the case where an import is used
                    handleMap(version, imports, (Map)mapValue);
                }
            } else {
                handleMapValue(version, imports, mapValue);
            }
        }
    }

    private void handleMapValue(Version version, Map<String, SourceFile> imports,
        Object mapValue) {
        if (mapValue instanceof Map) {
            handleMap(version, imports, (Map)mapValue);
        } else if (mapValue instanceof List) {
            for (Object listMember : (List)mapValue) {
                handleMapValue(version, imports, listMember);
            }
        }
    }

    private void handleImport(Version version, Map<String, SourceFile> imports, String mapValue) {
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_CWL;
        // create a new source file
        final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(fileType, version, mapValue);
        if (fileResponse == null) {
            SourceCodeRepoInterface.LOG.error("Could not read: " + mapValue);
            return;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(mapValue);
        imports.put(mapValue, sourceFile);
    }
}
