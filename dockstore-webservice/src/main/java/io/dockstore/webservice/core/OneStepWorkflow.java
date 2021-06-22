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
import javax.persistence.Table;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "OneStepWorkflow", description = "This describes one service in the dockstore as a special degenerate case of a workflow", parent = BioWorkflow.class)
@Entity
@Table(name = "onestepworkflow")

public class OneStepWorkflow extends BioWorkflow {

    //    @OneToOne(mappedBy = "checkerWorkflow", targetEntity = Entry.class, fetch = FetchType.EAGER)
    //    @JsonIgnore
    //    @ApiModelProperty(value = "The parent ID of a checker workflow. Null if not a checker workflow. Required for checker workflows.", position = 22)
    //    private Entry parentEntry;
    //
    //
    @Override
    public EntryType getEntryType() {
        return EntryType.ONESTEPWORKFLOW;
    }
    //
    //    @Override
    //    public Entry getParentEntry() {
    //        return parentEntry;
    //    }
    //
    //    @Column(columnDefinition = "boolean default false")
    //    private boolean isChecker = false;
    //
    //    @Override
    //    public void setParentEntry(Entry parentEntry) {
    //        this.parentEntry = parentEntry;
    //    }
    //
    //    @Override
    //    public boolean isIsChecker() {
    //        return this.isChecker;
    //    }
    //
    //    @Override
    //    public void setIsChecker(boolean isChecker) {
    //        this.isChecker = isChecker;
    //    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withOneStepWorkflow(this);
    }
}
