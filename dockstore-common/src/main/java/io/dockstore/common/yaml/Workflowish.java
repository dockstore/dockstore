package io.dockstore.common.yaml;

import java.util.List;

public interface Workflowish {

    String getName();
    List<YamlAuthor> getAuthors();
    Boolean getPublish();
    boolean getLatestTagAsDefault();
    Object getSubclass();
    Filters getFilters();
}
