package io.dockstore.common.yaml;

import java.util.List;

/**
 * Defines a common interface, implemented by the YamlWorkflow, YamlNotebook,
 * YamlTool, and Service12 classes, providing access to the properties that
 * are shared between different entry types, allowing the same code to be
 * used to process/register all of them, without conditionals or similar.
 */
public interface Workflowish {

    String getName();
    List<YamlAuthor> getAuthors();
    Boolean getPublish();
    boolean getLatestTagAsDefault();
    Object getSubclass();
    Filters getFilters();
    List<String> getTestParameterFiles();
    String getPrimaryDescriptorPath();
    Boolean getEnableAutoDois();

    /**
     * Optional: Document a specific readme path that can override the base readme file.
     * @return
     */
    String getReadMePath();

    String getTopic();

    /**
     * Get the list of user-specified "other" files that should be
     * read and included with the entry.
     */
    List<String> getOtherFiles();
}
