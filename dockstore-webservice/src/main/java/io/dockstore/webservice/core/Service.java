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

import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "Service", description = "This describes one service in the dockstore as a special degenerate case of a workflow", parent = Workflow.class)
@Entity
@Table(name = "service")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Service.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.WorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName) from Service c where c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Service.getEntryLiteByUserId", query = "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteService(c.sourceControl, c.organization, c.repository, c.workflowName, c.dbUpdateDate as edbUpdateDate, MAX(v.dbUpdateDate) as vdbUpdateDate) "
                + "FROM Service c LEFT JOIN c.workflowVersions v "
                + "WHERE c.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
                + "GROUP BY c.sourceControl, c.organization, c.repository, c.workflowName, c.dbUpdateDate")
})
public class Service extends Workflow {

    public enum SubClass { DOCKER_COMPOSE, SWARM, KUBERNETES, HELM }

    @Override
    public Entry getParentEntry() {
        return null;
    }

    public EntryType getEntryType() {
        return EntryType.SERVICE;
    }

    @Override
    public void setParentEntry(Entry parentEntry) {
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

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withService(this);
    }
}
