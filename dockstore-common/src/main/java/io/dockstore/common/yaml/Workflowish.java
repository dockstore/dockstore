package io.dockstore.common.yaml;

import java.util.List;

public interface Workflowish {

    public String getName();
    public List<YamlAuthor> getAuthors();
    public Boolean getPublish();
    public boolean getLatestTagAsDefault();
    public Object getSubclass();
    public Filters getFilters();
}
