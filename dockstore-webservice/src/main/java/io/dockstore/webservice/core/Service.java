/*
 *    Copyright 2019 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.core;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@ApiModel(value = "Service", description = Service.SERVICE_DESCRIPTION, parent = Workflow.class)
@Schema(name = "Service", description = Service.SERVICE_DESCRIPTION)

@Entity
@Table(name = "service")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Service.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.WorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName) from Service c where c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Service.getEntryLiteByUserId", query =
        "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteService(s.sourceControl, s.organization, s.repository, s.workflowName, s.dbUpdateDate as entryUpdated, MAX(v.dbUpdateDate) as versionUpdated) "
            + "FROM Service s LEFT JOIN s.workflowVersions v "
            + "WHERE s.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
            + "GROUP BY s.sourceControl, s.organization, s.repository, s.workflowName, s.dbUpdateDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.Service.getEntriesByUserId", query = "SELECT s FROM Service s WHERE s.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)")
})
public class Service extends Workflow {

    public static final String SERVICE_DESCRIPTION = "This describes one service in the dockstore as a special degenerate case of a workflow";
    public static final String OPENAPI_NAME = "Service";


    public enum SubClass { DOCKER_COMPOSE, SWARM, KUBERNETES, HELM
    }

    public Service() {
        super.setType(OPENAPI_NAME);
    }

    @Override
    @OneToOne
    @Schema(hidden = true)
    public Entry<?, ?> getParentEntry() {
        return null;
    }

    @Override
    public EntryType getEntryType() {
        return EntryType.SERVICE;
    }

    @Override
    public EntryTypeMetadata getEntryTypeMetadata() {
        return EntryTypeMetadata.SERVICE;
    }

    @Override
    public void setParentEntry(Entry<?, ?> parentEntry) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Service");
    }

    @Override
    public boolean isIsChecker() {
        return false;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Service");
    }

    @Transient
    public Event.Builder getEventBuilder() {
        return new Event.Builder().withService(this);
    }
}
