package io.dockstore.common.yaml;

import java.util.List;

public interface DockstoreYaml12AndUp extends DockstoreYaml {
    String getVersion();
    List<Workflowish> getEntries();
    List<String> getEntryTerms();
}
