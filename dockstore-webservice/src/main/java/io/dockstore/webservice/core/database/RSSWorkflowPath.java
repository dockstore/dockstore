/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.core.database;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import java.util.Date;

/**
 * This class is only used to get data from the database in a more type-safe way
 * @author gluu
 * @since 1.8.0
 */
public class RSSWorkflowPath {
    private final BioWorkflow bioWorkflow = new BioWorkflow();

    public RSSWorkflowPath(SourceControl sourceControl, String organization, String repository, String entryName, Date lastUpdated, String description) {
        this.bioWorkflow.setSourceControl(sourceControl);
        this.bioWorkflow.setOrganization(organization);
        this.bioWorkflow.setRepository(repository);
        this.bioWorkflow.setWorkflowName(entryName);
        this.bioWorkflow.setLastUpdated(lastUpdated);
        this.bioWorkflow.setDescription(description);
    }

    public BioWorkflow getBioWorkflow() {
        return bioWorkflow;
    }
}
