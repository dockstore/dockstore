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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import io.dockstore.client.Bridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.parser.WdlParser;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);
    private final SourceCodeRepoInterface sourceCodeRepoInterface;

    WDLHandler(SourceCodeRepoInterface sourceCodeRepoInterface) {
        this.sourceCodeRepoInterface = sourceCodeRepoInterface;
    }

    @Override
    public Entry parseWorkflowContent(Entry entry, String content) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        try {
            WdlParser parser = new WdlParser();
            WdlParser.TokenStream tokens;
            if (entry.getClass().equals(Tool.class)) {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Tool)entry).getDefaultWdlPath())));
            } else {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Workflow)entry).getDefaultWorkflowPath())));
            }
            WdlParser.Ast ast = (WdlParser.Ast)parser.parse(tokens).toAst();

            if (ast == null) {
                LOG.info("Error with WDL file.");
            } else {
                LOG.info("Repository has Dockstore.wdl");
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return entry;
    }

    @Override
    public boolean isValidWorkflow(String content) {
        // TODO: this code looks like it was broken at some point, needs investigation
        //        final NamespaceWithWorkflow nameSpaceWithWorkflow = NamespaceWithWorkflow.load(content);
        //        if (nameSpaceWithWorkflow != null) {
        //            return true;
        //        }
        //
        //        return false;
        // For now as long as a file exists, it is a valid WDL
        return true;
    }

    @Override
    public Map<String, SourceFile> processImports(String content, Version version) {
        Map<String, SourceFile> imports = new HashMap<>();
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_WDL;
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

                final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(fileType, version, importPath);
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
    }

    /**
     * Odd un-used method that seems like it could be useful
     * @param workflowFile
     * @return
     */
    List<String> getWdlImports(File workflowFile) {
        Bridge bridge = new Bridge();
        return bridge.getImportFiles(workflowFile);
    }
}
