/*
 * Copyright 2023 OICR, UCSC
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
import io.dockstore.webservice.core.Notebook;

public class NotebookPath {
    private final Notebook notebook = new Notebook();

    public NotebookPath(SourceControl sourceControl, String organization, String repository, String notebookName) {
        this.notebook.setSourceControl(sourceControl);
        this.notebook.setOrganization(organization);
        this.notebook.setRepository(repository);
        this.notebook.setWorkflowName(notebookName);
    }

    public Notebook getNotebook() {
        return notebook;
    }
}
