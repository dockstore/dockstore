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

public class DOIMessage extends BasicMessage {
    private String targetEntry;
    private long entryId;
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
