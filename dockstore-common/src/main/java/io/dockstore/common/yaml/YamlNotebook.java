// TODO insert copyright

package io.dockstore.common.yaml;

import java.util.List;

/**
 * A notebook as described in a .dockstore.yml
 */
public class YamlNotebook extends YamlWorkflow {

    private List<String> userPaths;

    public List<String> getUserPaths() {
        return userPaths;
    }
 
    public void setUserPaths(List<String> userPaths) {
        this.userPaths = userPaths;
    }

    public String getTerm(boolean isPlural) {
        return isPlural ? "notebooks" : "notebook";
    }
}
