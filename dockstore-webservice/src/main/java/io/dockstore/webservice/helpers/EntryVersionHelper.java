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

package io.dockstore.webservice.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;

/**
 * This interface contains code for interacting with the files of versions for all types of entries (currently, tools and workflows)
 * <p>
 * Created by dyuen on 10/03/16.
 */
public interface EntryVersionHelper<T extends Entry> extends AuthenticatedResourceInterface {

    /**
     * Implementors of this interface require a DAO
     */
    EntryDAO getDAO();

    /**
     * For the purposes of display, this method filters an entry to not show workflow or tool versions that are hidden
     * without deleting them from the database
     * @param entry the entry to be filtered
     * @return the filtered entry
     */
    default T filterContainersForHiddenTags(T entry) {
        if (entry == null) {
            return null;
        } else {
            return filterContainersForHiddenTags(Lists.newArrayList(entry)).get(0);
        }
    }

    /**
     * For convenience, filters a list of entries
     * @see EntryVersionHelper#filterContainersForHiddenTags(Entry)
     */
    default List<T> filterContainersForHiddenTags(List<T> entries) {
        for (T entry : entries) {
            getDAO().evict(entry);
            // clear users which are also lazy loaded
            entry.setUsers(null);
            // need to have this evicted so that hibernate does not actually delete the tags and users
            Set<Version> versions = entry.getVersions();
            versions.removeIf(Version::isHidden);
        }
        return entries;
    }

    /**
     * Return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     *
     * @param entryId  internal id for an entry
     * @param tag      github reference
     * @param fileType narrow the file to a specific type
     * @return return the primary descriptor or Dockerfile
     */
    default SourceFile getSourceFile(long entryId, String tag, SourceFile.FileType fileType) {
        return getSourceFileByPath(entryId, tag, fileType, null);
    }

    /**
     * If path is null, return the first file with the correct path that matches.
     * If path is not null, return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     *
     * @param entryId  internal id for an entry
     * @param tag      github reference
     * @param fileType narrow the file to a specific type
     * @param path     a specific path to a file
     * @return a single file depending on parameters
     */
    default SourceFile getSourceFileByPath(long entryId, String tag, SourceFile.FileType fileType, String path) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(entryId, tag, fileType);
        for (Map.Entry<String, ImmutablePair<SourceFile, FileDescription>> entry : sourceFiles.entrySet()) {
            if (path != null) {
                //db stored paths are absolute, convert relative to absolute
                if (entry.getKey().equals(path)) {
                    return entry.getValue().getLeft();
                }
            } else if (entry.getValue().getRight().primaryDescriptor) {
                return entry.getValue().getLeft();
            }
        }
        throw new CustomWebApplicationException("No descriptor found", HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Returns just the secondary source files for a particular version of a workflow
     * @param workflowId database id for the workflow
     * @param tag version of the workflow
     * @param fileType the filetype we want to consider
     * @return a list of SourceFile
     */
    default List<SourceFile> getAllSecondaryFiles(long workflowId, String tag, SourceFile.FileType fileType) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType);
        return sourceFiles.entrySet().stream()
            .filter(entry -> entry.getValue().getLeft().getType().equals(fileType) && !entry.getValue().right.primaryDescriptor)
            .map(entry -> entry.getValue().getLeft()).collect(Collectors.toList());
    }

    /**
     * Returns just the source files for a particular version of a workflow
     * @param workflowId database id for the workflow
     * @param tag version of the workflow
     * @param fileType the filetype we want to consider
     * @return a list of SourceFile
     */
    default List<SourceFile> getAllSourceFiles(long workflowId, String tag, SourceFile.FileType fileType) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType);
        return sourceFiles.entrySet().stream().filter(entry -> entry.getValue().getLeft().getType().equals(fileType))
            .map(entry -> entry.getValue().getLeft()).collect(Collectors.toList());
    }

    /**
     * This returns a map of file paths -> pairs of sourcefiles and descriptions of those sourcefiles
     * @param workflowId the database id for a workflow
     * @param tag the version of the workflow
     * @param fileType the type of file we're interested in
     * @return a map of file paths -> pairs of sourcefiles and descriptions of those sourcefiles
     */
    default Map<String, ImmutablePair<SourceFile, FileDescription>> getSourceFiles(long workflowId, String tag,
            SourceFile.FileType fileType) {
        T entry = (T)getDAO().findById(workflowId);
        checkEntry(entry);
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
            Version t = (Version)o;
            if (t.getName().equals(tag)) {
                tagInstance = t;
            }
        }

        if (tagInstance == null) {
            throw new CustomWebApplicationException("Invalid or missing tag " + tag + ".", HttpStatus.SC_BAD_REQUEST);
        }

        if (tagInstance instanceof WorkflowVersion) {
            final WorkflowVersion workflowVersion = (WorkflowVersion)tagInstance;
            List<SourceFile> filteredTypes = workflowVersion.getSourceFiles().stream()
                .filter(file -> Objects.equals(file.getType(), fileType)).collect(Collectors.toList());
            for (SourceFile file : filteredTypes) {
                if (fileType == SourceFile.FileType.CWL_TEST_JSON || fileType == SourceFile.FileType.WDL_TEST_JSON || fileType == SourceFile.FileType.NEXTFLOW_TEST_PARAMS) {
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(true)));
                } else {
                    // looks like this takes into account a potentially different workflow path for a specific version of a workflow
                    final String workflowPath = workflowVersion.getWorkflowPath();
                    final String workflowVersionPath = workflowVersion.getWorkflowPath();
                    final String actualPath =
                        workflowVersionPath == null || workflowVersionPath.isEmpty() ? workflowPath : workflowVersionPath;
                    boolean isPrimary = file.getType() == fileType && file.getPath().equalsIgnoreCase(actualPath);
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
                }
            }
        } else {
            final Tool tool = (Tool)entry;
            final Tag toolTag = (Tag)tagInstance;
            List<SourceFile> filteredTypes = toolTag.getSourceFiles().stream().filter(file -> Objects.equals(file.getType(), fileType))
                .collect(Collectors.toList());
            for (SourceFile file : filteredTypes) {
                // dockerfile is a special case since there always is only a max of one
                if (fileType == SourceFile.FileType.DOCKERFILE || fileType == SourceFile.FileType.CWL_TEST_JSON
                    || fileType == SourceFile.FileType.WDL_TEST_JSON) {
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(true)));
                    continue;
                }

                final String toolPath;
                String toolVersionPath;
                if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                    toolPath = tool.getDefaultCwlPath();
                    toolVersionPath = toolTag.getCwlPath();
                } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                    toolPath = tool.getDefaultWdlPath();
                    toolVersionPath = toolTag.getWdlPath();
                } else {
                    throw new CustomWebApplicationException("Format " + fileType + " not valid", HttpStatus.SC_BAD_REQUEST);
                }

                final String actualPath = (toolVersionPath == null || toolVersionPath.isEmpty()) ? toolPath : toolVersionPath;
                boolean isPrimary = file.getType() == fileType && actualPath.equalsIgnoreCase(file.getPath());
                resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
            }
        }
        return resultMap;
    }



    default void createTestParameters(List<String> testParameterPaths, Version workflowVersion, Set<SourceFile> sourceFiles, SourceFile.FileType fileType, FileDAO fileDAO) {
        for (String path : testParameterPaths) {
            long sourcefileDuplicate = sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType)
                .count();
            if (sourcefileDuplicate == 0) {
                // Sourcefile doesn't exist, add a stub which will have it's content filled on refresh
                SourceFile sourceFile = new SourceFile();
                sourceFile.setPath(path);
                sourceFile.setType(fileType);

                long id = fileDAO.create(sourceFile);
                SourceFile sourceFileWithId = fileDAO.findById(id);
                workflowVersion.addSourceFile(sourceFileWithId);
            }
        }
    }

    /**
     * Looks like this is used just to denote which descriptor is the primary descriptor
     */
    class FileDescription {
        boolean primaryDescriptor;

        FileDescription(boolean isPrimaryDescriptor) {
            this.primaryDescriptor = isPrimaryDescriptor;
        }
    }
}
