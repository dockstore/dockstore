/*
 * Copyright 2022 OICR, UCSC
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
import io.dockstore.webservice.core.AppTool;
import java.util.Date;

public class RSSAppToolPath {
    private final AppTool appTool = new AppTool();

    public RSSAppToolPath(SourceControl sourceControl, String organization, String repository, String entryName, Date lastUpdated, String description) {
        this.appTool.setSourceControl(sourceControl);
        this.appTool.setOrganization(organization);
        this.appTool.setRepository(repository);
        this.appTool.setWorkflowName(entryName);
        this.appTool.setLastUpdated(lastUpdated);
        this.appTool.setDescription(description);
    }

    public AppTool getAppTool() {
        return appTool;
    }
}
