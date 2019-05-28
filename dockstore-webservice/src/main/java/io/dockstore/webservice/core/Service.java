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

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;

import io.swagger.annotations.ApiModel;

@ApiModel(value = "Service", description = "This describes one service in the dockstore as a special degenerate case of a workflow")
@Entity
@DiscriminatorValue("service")
@Table(name = "service")
public class Service extends Workflow {

    @Override
    public Entry getParentEntry() {
        return null;
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
}
