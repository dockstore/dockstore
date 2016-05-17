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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;

import com.google.common.collect.Lists;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
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
                String path = null;
                T entry = (T)dao.findById(workflowId);
                Helper.checkEntry(entry);
                this.filterContainersForHiddenTags(entry);
                Version tagInstance = null;

                if (tag == null) {
                        // This is an assumption made for quay tools. Workflows will not have a latest unless it is created by the user,
                        // and would thus make more sense to use master for workflows.
                        tag = "latest";
                }

                // todo: why the cast here?
                for (Object o : entry.getVersions()) {
                        Version t = (Version)o;
                        if (t.getName().equals(tag)) {
                                tagInstance = t;
                        }
                }

                if (entry instanceof Workflow) {
                        WorkflowVersion workflowVersion = (WorkflowVersion) tagInstance;
                        path = workflowVersion.getWorkflowPath();
                } else {
                        Tag tagVersion = (Tag) tagInstance;
                        if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                                path = tagVersion.getCwlPath();
                        } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                                path = tagVersion.getWdlPath();
                        } else if (fileType == SourceFile.FileType.DOCKERFILE) {
                                path = tagVersion.getDockerfilePath();
                        }
                }


                if (tagInstance == null) {
                        throw new CustomWebApplicationException("Invalid version.", HttpStatus.SC_BAD_REQUEST);
                } else {
                        for (Object o : tagInstance.getSourceFiles()) {
                                SourceFile file = (SourceFile)o;
                                if (file.getType() == fileType && ((SourceFile) o).getPath().equals(path)) {
                                        return file;
                                }
                        }
                }
                throw new CustomWebApplicationException("File not found.", HttpStatus.SC_NOT_FOUND);
        }

        public List<SourceFile> getSourceFiles(Long workflowId, String tag, SourceFile.FileType fileType) {
                T entry = (T)dao.findById(workflowId);
                Helper.checkEntry(entry);
                this.filterContainersForHiddenTags(entry);
                Version tagInstance = null;
                ArrayList<SourceFile> sourceFiles = new ArrayList<>();

                if (tag == null) {
                        // This is an assumption made for quay tools. Workflows will not have a latest unless it is created by the user,
                        // and would thus make more sense to use master for workflows.
                        tag = "latest";
                }

                // todo: why the cast here?
                for (Object o : entry.getVersions()) {
                        Version t = (Version) o;
                        if (t.getName().equals(tag)) {
                                tagInstance = t;
                        }
                }

                if (tagInstance == null) {
                        throw new CustomWebApplicationException("Invalid version.", HttpStatus.SC_BAD_REQUEST);
                } else {
                        if (tagInstance instanceof WorkflowVersion) {
                                for (Object o : tagInstance.getSourceFiles()) {
                                        SourceFile file = (SourceFile) o;
                                        if (file.getType() == fileType && !(((SourceFile) o).getPath().equals(((WorkflowVersion) tagInstance).getWorkflowPath()))) {
                                                sourceFiles.add(file);
                                        }
                                }
                        } else {
                                String descPath = null;
                                if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                                        descPath = ((Tag)tagInstance).getCwlPath();
                                } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL){
                                        descPath = ((Tag)tagInstance).getWdlPath();
                                }
                                for (Object o : tagInstance.getSourceFiles()) {
                                        SourceFile file = (SourceFile) o;
                                        if (file.getType() == fileType && !(((SourceFile) o).getPath().equals(descPath))) {
                                                sourceFiles.add(file);
                                        }
                                }
                        }
                        return sourceFiles;
                }
        }
}
