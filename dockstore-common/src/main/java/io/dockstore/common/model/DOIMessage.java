/*
 *    Copyright 2017 OICR
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
package io.dockstore.common.model;

/**
 * A message that indicates a request for the creation of a DOI.
 *
 */
public class DOIMessage extends BasicMessage {
    /**
     * The type of entry that we should generate a DOI for (tool or workflow).
     * This can probably be cleaner
     */
    private String targetEntry;
    /**
     * The database id for the tool or workflow
     */
    private long entryId;
    /**
     * The database id for the version of the tool or workflow
     */
    private long entryVersionId;

    public DOIMessage() {

    }

    public String getTargetEntry() {
        return targetEntry;
    }

    public void setTargetEntry(String targetEntry) {
        this.targetEntry = targetEntry;
    }

    public long getEntryId() {
        return entryId;
    }

    public void setEntryId(long entryId) {
        this.entryId = entryId;
    }

    public long getEntryVersionId() {
        return entryVersionId;
    }

    public void setEntryVersionId(long entryVersionId) {
        this.entryVersionId = entryVersionId;
    }
}
