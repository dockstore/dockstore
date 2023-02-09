package io.dockstore.common.yaml;

import java.util.List;

/**
 * Defines a common interface, implemented by the Service12 and
 * YamlWorkflow classes, that allows us to gracefully process instances
 * of them with the same code.
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
}
