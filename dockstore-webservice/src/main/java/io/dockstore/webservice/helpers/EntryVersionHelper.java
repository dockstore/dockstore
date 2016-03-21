/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.helpers;

import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;

import com.google.common.collect.Lists;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.EntryDAO;

/**
 * This class contains code for interacting with versions for all types of entries.
 *
 * Created by dyuen on 10/03/16.
 */
public class EntryVersionHelper<T extends Entry> {

        private final EntryDAO dao;

        public EntryVersionHelper(EntryDAO dao){
                this.dao = dao;
        }

        public T filterContainersForHiddenTags(T entry){
                return filterContainersForHiddenTags(Lists.newArrayList(entry)).get(0);
        }

        public List<T> filterContainersForHiddenTags(List<T> entries) {
                for(T entry : entries){
                        dao.evict(entry);
                        // need to have this evict so that hibernate does not actually delete the tags
                        Set<Version> versions = entry.getVersions();
                        versions.removeIf(Version::isHidden);
                }
                return entries;
        }

        public SourceFile getSourceFile(Long workflowId, String tag, SourceFile.FileType fileType) {
                T entry = (T)dao.findById(workflowId);
                Helper.checkEntry(entry);
                this.filterContainersForHiddenTags(entry);
                Version tagInstance = null;

                if (tag == null) {
                        tag = "latest";
                }

                // todo: why the cast here?
                for (Object o : entry.getVersions()) {
                        Version t = (Version)o;
                        if (t.getName().equals(tag)) {
                                tagInstance = t;
                        }
                }

                if (tagInstance == null) {
                        throw new CustomWebApplicationException("Invalid version.", HttpStatus.SC_BAD_REQUEST);
                } else {
                        for (Object o : tagInstance.getSourceFiles()) {
                                SourceFile file = (SourceFile)o;
                                if (file.getType() == fileType) {
                                        return file;
                                }
                        }
                }
                throw new CustomWebApplicationException("File not found.", HttpStatus.SC_NOT_FOUND);
        }
}
