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

package io.dockstore.webservice.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.dockstore.common.SourceControl;

/**
 * This class is only used to get data from the database in a more type-safe way
 * @author gluu
 * @since 1.8.0
 */
public class RSSWorkflowPath extends RSSEntryPath {
    private final SourceControl sourceControl;
    private final String organization;
    private final String repository;
    private final String workflowName;

    public RSSWorkflowPath(SourceControl sourceControl, String organization, String repository, String workflowName, Date lastUpdated, String description) {
        this.sourceControl = sourceControl;
        this.organization = organization;
        this.repository = repository;
        this.workflowName = workflowName;
        this.lastUpdated = lastUpdated;
        this.description = description;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public String getOrganization() {
        return organization;
    }

    public String getRepository() {
        return repository;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getEntryPath() {
        List<String> segments = new ArrayList<>();
        segments.add(sourceControl.toString());
        segments.add(organization);
        segments.add(repository);
        if (workflowName != null && !workflowName.isEmpty()) {
            segments.add(workflowName);
        }
        return String.join("/", segments);
    }
}
