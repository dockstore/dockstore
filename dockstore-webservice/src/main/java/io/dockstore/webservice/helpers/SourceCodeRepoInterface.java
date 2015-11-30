package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.yamlbeans.YamlReader;

import io.dockstore.webservice.core.Container;

/**
 * @author dyuen
 */
public abstract class SourceCodeRepoInterface {

    public static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);

    public abstract FileResponse readFile(String fileName, String reference);

    /**
     * Update a container with the contents of the CWL file from a source code repo
     * 
     * @param c
     *            a container to be updated
     * @return an updated container with fields from the CWL filled in
     */
    public abstract Container findCWL(Container c);

    /**
     *
     * @param gitURL
     *            a git url
     * @param reference
     *            a raw reference from git like "refs/heads/master"
     * @return the last segment like master
     */
    public String getReference(String gitURL, String reference) {
        Map<String, String> map = SourceCodeRepoFactory.parseGitUrl(gitURL);
        if (map == null) {
            return null;
        }
        if (reference != null) {
            Pattern p = Pattern.compile("(\\S+)/(\\S+)/(\\S+)");
            Matcher m = p.matcher(reference);
            if (!m.find()) {
                LOG.info("Cannot parse reference: " + reference);
                return null;
            }

            // These correspond to the positions of the pattern matcher
            final int refIndex = 3;

            reference = m.group(refIndex);
            LOG.info("REFERENCE: " + reference);
        }
        return reference;
    }

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

                container.setHasCollab(true);
                LOG.info("Repository has Dockstore.cwl");
            } catch (IOException ex) {
                LOG.info("CWL file is malformed");
                ex.printStackTrace();
            }
        }
        return container;
    }

    public static class FileResponse {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return this.content;
        }
    }
}
