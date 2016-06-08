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

import com.google.common.collect.Lists;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.EntryDAO;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class contains code for interacting with versions for all types of entries.
 * <p>
 * Created by dyuen on 10/03/16.
 */
public class EntryVersionHelper<T extends Entry> {

    private final EntryDAO dao;

    public EntryVersionHelper(EntryDAO dao) {
        this.dao = dao;
    }

    public T filterContainersForHiddenTags(T entry) {
        return filterContainersForHiddenTags(Lists.newArrayList(entry)).get(0);
    }

    public List<T> filterContainersForHiddenTags(List<T> entries) {
        for (T entry : entries) {
            dao.evict(entry);
            // need to have this evict so that hibernate does not actually delete the tags
            Set<Version> versions = entry.getVersions();
            versions.removeIf(Version::isHidden);
        }
        return entries;
    }

    /**
     * Return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     * @param entryId internal id for an entry
     * @param tag github reference
     * @param fileType narrow the file to a specific type
     * @return return the primary descriptor or Dockerfile
     */
    public SourceFile getSourceFile(long entryId, String tag, SourceFile.FileType fileType) {
        return getSourceFileByPath(entryId, tag, fileType, null);
    }

    /**
     * If path is null, return the first file with the correct path that matches.
     * If path is not null, return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     * @param entryId internal id for an entry
     * @param tag github reference
     * @param fileType narrow the file to a specific type
     * @param path a specific path to a file
     * @return a single file depending on parameters
     */
    public SourceFile getSourceFileByPath(long entryId, String tag, SourceFile.FileType fileType, String path) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(entryId, tag, fileType);
        for(Map.Entry<String, ImmutablePair<SourceFile, FileDescription>> entry : sourceFiles.entrySet()){
            if (path != null) {
                //db stored paths are absolute, convert relative to absolute
                String relativePath = "/" + path;
                if (entry.getKey().equals(relativePath)) {
                    return entry.getValue().getLeft();
                }
            } else if (entry.getValue().getRight().primaryDescriptor){
                return entry.getValue().getLeft();
            }
        }
        throw new CustomWebApplicationException("No descriptor found", HttpStatus.SC_BAD_REQUEST);
    }

    public List<SourceFile> getAllSecondaryFiles(long workflowId, String tag, SourceFile.FileType fileType) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType);
        List<SourceFile> files = Lists.newArrayList();
        for(Map.Entry<String, ImmutablePair<SourceFile, FileDescription>> entry : sourceFiles.entrySet()){
            if (entry.getValue().getLeft().getType().equals(fileType) && !entry.getValue().right.primaryDescriptor){
                files.add(entry.getValue().getLeft());
            }
        }
        return files;
    }

    public List<SourceFile> getAllSourceFiles(long workflowId, String tag, SourceFile.FileType fileType) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType);
        List<SourceFile> files = Lists.newArrayList();
        for(Map.Entry<String, ImmutablePair<SourceFile, FileDescription>> entry : sourceFiles.entrySet()){
            if (entry.getValue().getLeft().getType().equals(fileType)){
                files.add(entry.getValue().getLeft());
            }
        }
        return files;
    }


    private Map<String, ImmutablePair<SourceFile, FileDescription>> getSourceFiles(long workflowId, String tag,
            SourceFile.FileType fileType) {
        T entry = (T) dao.findById(workflowId);
        Helper.checkEntry(entry);
        this.filterContainersForHiddenTags(entry);
        Version tagInstance = null;

        Map<String, ImmutablePair<SourceFile, FileDescription>> resultMap = new HashMap<>();

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
                final WorkflowVersion workflowVersion = (WorkflowVersion) tagInstance;
                for (SourceFile file : workflowVersion.getSourceFiles()) {
                    final String workflowPath = workflowVersion.getWorkflowPath();
                    final String workflowVersionPath = workflowVersion.getWorkflowPath();
                    final String actualPath =
                            workflowVersionPath == null || workflowVersionPath.isEmpty() ? workflowPath : workflowVersionPath;
                    boolean isPrimary = file.getType() == fileType && file.getPath().equalsIgnoreCase(actualPath);
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
                }
            } else {
                final Tool tool = (Tool) entry;
                final Tag workflowVersion = (Tag) tagInstance;
                for (SourceFile file : workflowVersion.getSourceFiles()) {
                    // dockerfile is a special case since there always is only a max of one
                    if (fileType == SourceFile.FileType.DOCKERFILE){
                        if (file.getType() == SourceFile.FileType.DOCKERFILE) {
                            resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(true)));
                        }
                        continue;
                    }

                    final String workflowPath;
                    if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                        workflowPath = tool.getDefaultCwlPath();
                    } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                        workflowPath = tool.getDefaultWdlPath();
                    } else{
                        throw new CustomWebApplicationException("Format " + fileType + " not valid", HttpStatus.SC_BAD_REQUEST);
                    }

                    String workflowVersionPath;
                    if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                        workflowVersionPath = workflowVersion.getCwlPath();
                    } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                        workflowVersionPath = workflowVersion.getWdlPath();
                    } else{
                        throw new CustomWebApplicationException("Format " + fileType + " not valid", HttpStatus.SC_BAD_REQUEST);
                    }

                    final String actualPath =
                            (workflowVersionPath == null || workflowVersionPath.isEmpty()) ? workflowPath : workflowVersionPath;
                    boolean isPrimary = file.getType() == fileType && actualPath.equalsIgnoreCase(file.getPath());
                    if (fileType == file.getType()) {
                        resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
                    }
                }
            }
            return resultMap;
        }
    }

    private class FileDescription {
        boolean primaryDescriptor;
        FileDescription(boolean isPrimaryDescriptor){
            this.primaryDescriptor = isPrimaryDescriptor;
        }
    }
}
