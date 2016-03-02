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

import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.yamlbeans.YamlReader;

import io.dockstore.webservice.core.Container;
import wdl4s.parser.WdlParser;

/**
 * @author dyuen
 */
public abstract class SourceCodeRepoInterface {

    public static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoInterface.class);

    public abstract FileResponse readFile(String fileName, String reference);

    /**
     * Update a container with the contents of the descriptor file from a source code repo
     * 
     * @param c
     *            a container to be updated
     * @return an updated container with fields from the descriptor filled in
     */
    public abstract Container findDescriptor(Container c, String fileName);

    public abstract String getOrganizationEmail();

    /**
     * Parses the cwl content to get the author and description. Updates the container with the author, description, and hasCollab fields.
     *
     * @param container
     *            a container to be updated
     * @param content
     *            a cwl document
     * @return the updated container
     */
    protected Container parseCWLContent(Container container, String content) {
        // parse the collab.cwl file to get description and author
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map) object;

                String description = (String) map.get("description");
                if (description != null) {
                    container.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                map = (Map) map.get("dct:creator");
                if (map != null) {
                    String author = (String) map.get("foaf:name");
                    container.setAuthor(author);
                } else {
                    LOG.info("Creator not found!");
                }

                // container.setHasCollab(true);
                container.setValidTrigger(true);
                LOG.info("Repository has Dockstore.cwl");
            } catch (IOException ex) {
                LOG.info("CWL file is malformed");
                ex.printStackTrace();
            }
        }
        return container;
    }

    protected Container parseWDLContent(Container container, String content) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        try {
            String wdlSource = content;
            WdlParser parser = new WdlParser();
            WdlParser.TokenStream tokens = new WdlParser.TokenStream(parser.lex(wdlSource, FilenameUtils.getName(container.getDefaultWdlPath())));
            WdlParser.Ast ast = (WdlParser.Ast) parser.parse(tokens).toAst();

            if (ast == null) {
                LOG.info("Error with WDL file.");
            } else {
                container.setValidTrigger(true);
                LOG.info("Repository has Dockstore.wdl");
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return container;
    }

    public static class FileResponse {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}
