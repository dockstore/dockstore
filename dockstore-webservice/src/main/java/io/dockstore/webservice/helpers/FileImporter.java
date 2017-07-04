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

package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generically imports files in order to populate Tools and Workflows.
 */
public class FileImporter {

    public static final Logger LOG = LoggerFactory.getLogger(FileImporter.class);
    private final SourceCodeRepoInterface sourceCodeRepo;

    public FileImporter(SourceCodeRepoInterface sourceCodeRepo) {
        this.sourceCodeRepo = sourceCodeRepo;
    }

    /**
     * Look in a source code repo for a particular file
     * @param fileType
     * @param version
     * @param specificPath if specified, look for a specific file, otherwise return the "default" for a fileType
     * @return  a FileResponse instance
     */
    public String readGitRepositoryFile(SourceFile.FileType fileType, Version version, String specificPath) {

        if (sourceCodeRepo == null) {
            return null;
        }

        final String reference = version.getReference();

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";
        if (specificPath != null) {
            String workingDirectory = version.getWorkingDirectory();
            if (specificPath.startsWith("/")) {
                // if we're looking at an absolute path, ignore the working directory
                fileName = specificPath;
            } else if (!workingDirectory.isEmpty() && !"/".equals(workingDirectory)) {
                // if the working directory is different from the root, take it into account
                fileName = workingDirectory + "/" +  specificPath;
            } else {
                fileName = specificPath;
            }
        } else if (version instanceof Tag) {
            Tag tag = (Tag)version;
            // Add for new descriptor types
            if (fileType == SourceFile.FileType.DOCKERFILE) {
                fileName = tag.getDockerfilePath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                if (Strings.isNullOrEmpty(tag.getCwlPath())) {
                    return null;
                }
                fileName = tag.getCwlPath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                if (Strings.isNullOrEmpty(tag.getWdlPath())) {
                    return null;
                }
                fileName = tag.getWdlPath();
            }
        } else if (version instanceof WorkflowVersion) {
            WorkflowVersion workflowVersion = (WorkflowVersion)version;
            fileName = workflowVersion.getWorkflowPath();
        }

        return sourceCodeRepo.readFile(fileName, reference);
    }

    public Map<String, SourceFile> resolveImports(String content, Entry entry, SourceFile.FileType fileType, Version version) {

        Map<String, SourceFile> imports = new HashMap<>();

        if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {

            YamlReader reader = new YamlReader(content);
            try {
                Map<String, ?> map = reader.read(Map.class);
                handleMap(entry, fileType, version, imports, map);

            } catch (YamlException e) {
                SourceCodeRepoInterface.LOG.error("Could not process content from " + entry.getId() + " as yaml");
            }

            Map<String, SourceFile> recursiveImports = new HashMap<>();
            for (SourceFile file : imports.values()) {
                final Map<String, SourceFile> sourceFiles = resolveImports(file.getContent(), entry, fileType, version);
                recursiveImports.putAll(sourceFiles);
            }
            recursiveImports.putAll(imports);
            return recursiveImports;
        } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
            final File tempDesc;
            try {
                tempDesc = File.createTempFile("temp", ".wdl", Files.createTempDir());
                Files.write(content, tempDesc, StandardCharsets.UTF_8);

                // Use matcher to get imports
                List<String> lines = FileUtils.readLines(tempDesc, StandardCharsets.UTF_8);
                ArrayList<String> importPaths = new ArrayList<>();
                Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"");

                for (String line : lines) {
                    Matcher m = p.matcher(line);

                    while (m.find()) {
                        String match = m.group(1);
                        if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                            importPaths.add(match.replaceFirst("file://", "")); // remove file:// from path
                        }
                    }
                }

                for (String importPath : importPaths) {
                    SourceFile importFile = new SourceFile();

                    final String fileResponse = readGitRepositoryFile(fileType, version, importPath);
                    if (fileResponse == null) {
                        SourceCodeRepoInterface.LOG.error("Could not read: " + importPath);
                        continue;
                    }
                    importFile.setContent(fileResponse);
                    importFile.setPath(importPath);
                    importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
                    imports.put(importFile.getPath(), importFile);
                }
            } catch (IOException e) {
                throw new CustomWebApplicationException("Internal server error, out of space",
                        HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE);
            }

            return imports;
        } else {
            throw new CustomWebApplicationException("Invalid file type for import", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void handleMap(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports,
            Map<String, ?> map) {
        for (Map.Entry<String, ?> e : map.entrySet()) {
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import") || e.getKey().equalsIgnoreCase("$include") || e.getKey().equalsIgnoreCase("import")
                    || e.getKey().equalsIgnoreCase("include")) {
                // handle imports and includes
                if (mapValue instanceof String) {
                    handleImport(fileType, version, imports, (String)mapValue);
                }
            } else if (e.getKey().equalsIgnoreCase("run")) {
                // for workflows, bare files may be referenced. See https://github.com/ga4gh/dockstore/issues/208
                //ex:
                //  run: {import: revtool.cwl}
                //  run: revtool.cwl
                if (mapValue instanceof String) {
                    handleImport(fileType, version, imports, (String)mapValue);
                } else if (mapValue instanceof Map) {
                    // this handles the case where an import is used
                    handleMap(entry, fileType, version, imports, (Map)mapValue);
                }
            } else {
                handleMapValue(entry, fileType, version, imports, mapValue);
            }
        }
    }

    private void handleMapValue(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports,
            Object mapValue) {
        if (mapValue instanceof Map) {
            handleMap(entry, fileType, version, imports, (Map)mapValue);
        } else if (mapValue instanceof List) {
            for (Object listMember : (List)mapValue) {
                handleMapValue(entry, fileType, version, imports, listMember);
            }
        }
    }

    private void handleImport(SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports, String mapValue) {
        // create a new source file
        final String fileResponse = readGitRepositoryFile(fileType, version, mapValue);
        if (fileResponse == null) {
            FileImporter.LOG.error("Could not read: " + mapValue);
            return;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(mapValue);
        imports.put(mapValue, sourceFile);
    }
}
