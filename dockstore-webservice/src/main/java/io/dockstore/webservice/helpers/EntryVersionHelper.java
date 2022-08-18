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

import com.google.api.client.util.Charsets;
import com.google.common.collect.Lists;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.AbstractDockstoreDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * This interface contains code for interacting with the files of versions for all types of entries (currently, tools and workflows)
 * <p>
 * Created by dyuen on 10/03/16.
 */
public interface EntryVersionHelper<T extends Entry<T, U>, U extends Version, W extends EntryDAO<T>>
    extends AuthenticatedResourceInterface {

    String CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY = "Cannot modify frozen versions this way";

    /**
     * Implementors of this interface require a DAO
     */
    W getDAO();

    /**
     * Sets the default version if possible, if not it will throw an error
     * @param version Name of the version to set
     * @param id Id of entry
     * @param user User
     */
    default Entry updateDefaultVersionHelper(String version, long id, User user) {
        Entry entry = getDAO().findById(id);
        checkNotNullEntry(entry);
        checkCanWrite(user, entry);
        if (version != null) {
            if (!entry.checkAndSetDefaultVersion(version)) {
                throw new CustomWebApplicationException("Given version does not exist.", HttpStatus.SC_NOT_FOUND);
            }
        }
        Entry result = getDAO().findById(id);
        checkNotNullEntry(result);
        entry.syncMetadataWithDefault();
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result;
    }

    /**
     * For the purposes of display, this method filters an entry to not show workflow or tool versions that are hidden
     * without deleting them from the database
     * @param entry the entry to be filtered
     * @return the filtered entry
     */
    default T filterContainersForHiddenTags(T entry) {
        Hibernate.initialize(entry.getWorkflowVersions());
        return filterContainersForHiddenTags(Lists.newArrayList(entry)).get(0);
    }

    /**
     * For convenience, filters a list of entries
     * @see EntryVersionHelper#filterContainersForHiddenTags(Entry)
     */
    default List<T> filterContainersForHiddenTags(List<T> entries) {
        for (T entry : entries) {
            Hibernate.initialize(entry.getWorkflowVersions());
            getDAO().evict(entry);
            // clear users which are also lazy loaded
            entry.setUsers(null);
            // need to have this evicted so that hibernate does not actually delete the tags and users
            Set<U> versions = entry.getWorkflowVersions();
            versions.removeIf(Version::isHidden);
        }
        return entries;
    }

    default void stripContent(List<? extends Entry> entries) {
        stripContentFromEntries(entries, getDAO());
    }

    /**
     * For convenience, filters a list of entries
     */
    static void stripContentFromEntries(List<? extends Entry> entries, AbstractDockstoreDAO dao) {
        for (Entry entry : entries) {
            dao.evict(entry);
            // clear users which are also lazy loaded
            entry.setUsers(null);
        }
    }

    /**
     * This function does the following: Saves the current state of the entry, forces the data between the session and the database to synchronize (flush),
     * and removes the entry from the session so that the cleared sourcefiles are not saved to the database.
     */
    static void removeSourceFilesFromEntry(Entry entry, SessionFactory sessionFactory) {
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.save(entry);
        currentSession.flush();
        currentSession.evict(entry);
        Set<Version> versions = entry.getWorkflowVersions();
        versions.forEach(version ->
                version.getSourceFiles().clear()
        );
    }

    default T updateLabels(User user, Long containerId, String labelStrings, LabelDAO labelDAO) {
        T c = getDAO().findById(containerId);
        checkNotNullEntry(c);
        checkCanWrite(user, c);

        EntryLabelHelper<T> labeller = new EntryLabelHelper<>(labelDAO);
        T entry = labeller.updateLabels(c, labelStrings);
        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);
        return entry;
    }

    /**
     * Return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     *
     * @param entryId    internal id for an entry
     * @param tag        github reference
     * @param fileType   narrow the file to a specific type
     * @param versionDAO
     * @return return the primary descriptor or Dockerfile
     */
    default SourceFile getSourceFile(long entryId, String tag, DescriptorLanguage.FileType fileType, Optional<User> user, FileDAO fileDAO,
            VersionDAO versionDAO) {
        return getSourceFileByPath(entryId, tag, fileType, null, user, fileDAO, versionDAO);
    }

    /**
     * If path is null, return the first file with the correct path that matches.
     * If path is not null, return the primary descriptor (i.e. the dockstore.cwl or dockstore.wdl usually, or a single Dockerfile)
     *
     * @param entryId    internal id for an entry
     * @param tag        github reference
     * @param fileType   narrow the file to a specific type
     * @param path       a specific path to a file
     * @param versionDAO
     * @return a single file depending on parameters
     */
    default SourceFile getSourceFileByPath(long entryId, String tag, DescriptorLanguage.FileType fileType, String path, Optional<User> user, FileDAO fileDAO,
            VersionDAO versionDAO) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(entryId, tag, fileType, user, fileDAO, versionDAO);
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
    default List<SourceFile> getAllSecondaryFiles(long workflowId, String tag, DescriptorLanguage.FileType fileType, Optional<User> user, FileDAO fileDAO, VersionDAO versionDAO) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType, user, fileDAO,
                versionDAO);
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
    default List<SourceFile> getAllSourceFiles(long workflowId, String tag, DescriptorLanguage.FileType fileType, Optional<User> user, FileDAO fileDAO, VersionDAO versionDAO) {
        final Map<String, ImmutablePair<SourceFile, FileDescription>> sourceFiles = this.getSourceFiles(workflowId, tag, fileType, user, fileDAO,
                versionDAO);
        return sourceFiles.entrySet().stream().filter(entry -> entry.getValue().getLeft().getType().equals(fileType))
            .map(entry -> entry.getValue().getLeft()).collect(Collectors.toList());
    }

    default Version findVersionByName(T entry, String versionName, VersionDAO versionDAO) {
        try {
            // This is an assumption made for quay tools. Workflows will not have a latest unless it is created by the user,
            // and would thus make more sense to use master for workflows.
            final String modifiedVersionName = versionName == null ? "latest" : versionName;
            versionDAO.enableNameFilter(modifiedVersionName);
            return entry.getWorkflowVersions().stream().filter(v -> v.getName().equals(modifiedVersionName)).findFirst().orElse(null);
        } finally {
            versionDAO.disableNameFilter();
        }
    }

    default Version findVersionById(long entryId, long versionId, VersionDAO versionDAO) {
        return versionDAO.findVersionInEntry(entryId, versionId);
    }

    default T findEntryById(long entryId, Optional<User> user) {

        T entry = getDAO().findById(entryId);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);

        // tighten permissions for hosted tools and workflows
        if (!user.isPresent() || !canExamine(user.get(), entry)) {
            if (!entry.getIsPublished()) {
                if (entry instanceof Tool && ((Tool)entry).getMode() == ToolMode.HOSTED) {
                    throw new CustomWebApplicationException("Entry not published", HttpStatus.SC_FORBIDDEN);
                }
                if (entry instanceof Workflow && ((Workflow)entry).getMode() == WorkflowMode.HOSTED) {
                    throw new CustomWebApplicationException("Entry not published", HttpStatus.SC_FORBIDDEN);
                }
            }
            this.filterContainersForHiddenTags(entry);
        }

        return entry;
    }

    /**
     * This returns a map of file paths -> pairs of sourcefiles and descriptions of those sourcefiles
     *
     * @param workflowId the database id for a workflow
     * @param tag        the version of the workflow
     * @param fileType   the type of file we're interested in
     * @param versionDAO
     * @return a map of file paths -> pairs of sourcefiles and descriptions of those sourcefiles
     */
    default Map<String, ImmutablePair<SourceFile, FileDescription>> getSourceFiles(long workflowId, String tag,
            DescriptorLanguage.FileType fileType, Optional<User> user, FileDAO fileDAO, VersionDAO versionDAO) {

        T entry = findEntryById(workflowId, user);
        Version tagInstance = findVersionByName(entry, tag, versionDAO);
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(entry.getId());
        return mapAndDescribe(sourceFiles, entry, tagInstance, fileType);
    }


    static Map<String, ImmutablePair<SourceFile, FileDescription>> mapAndDescribe(List<SourceFile> sourceFiles, Entry entry, Version tagInstance, DescriptorLanguage.FileType fileType) {    
        Map<String, ImmutablePair<SourceFile, FileDescription>> resultMap = new HashMap<>();
        List<SourceFile> filteredTypes = sourceFiles.stream()
            .filter(file -> Objects.equals(file.getType(), fileType)).collect(Collectors.toList());

        if (tagInstance instanceof WorkflowVersion) {
            final Workflow workflow = (Workflow)entry;
            final WorkflowVersion workflowVersion = (WorkflowVersion)tagInstance;
            for (SourceFile file : filteredTypes) {
                if (fileType == DescriptorLanguage.FileType.CWL_TEST_JSON || fileType == DescriptorLanguage.FileType.WDL_TEST_JSON || fileType == DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS) {
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(true)));
                } else {
                    // looks like this takes into account a potentially different workflow path for a specific version of a workflow
                    final String actualPath = StringUtils.firstNonEmpty(workflowVersion.getWorkflowPath(), workflow.getDefaultWorkflowPath());
                    boolean isPrimary = file.getPath().equalsIgnoreCase(actualPath);
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
                }
            }
        } else {
            final Tool tool = (Tool)entry;
            final Tag toolTag = (Tag)tagInstance;
            for (SourceFile file : filteredTypes) {
                // dockerfile is a special case since there always is only a max of one
                if (fileType == DescriptorLanguage.FileType.DOCKERFILE || fileType == DescriptorLanguage.FileType.CWL_TEST_JSON || fileType == DescriptorLanguage.FileType.WDL_TEST_JSON) {
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(true)));
                } else {
                    String actualPath;
                    if (fileType == DescriptorLanguage.FileType.DOCKSTORE_CWL) {
                        actualPath = StringUtils.firstNonEmpty(toolTag.getCwlPath(), tool.getDefaultCwlPath());
                    } else if (fileType == DescriptorLanguage.FileType.DOCKSTORE_WDL) {
                        actualPath = StringUtils.firstNonEmpty(toolTag.getWdlPath(), tool.getDefaultWdlPath());
                    } else {
                        throw new CustomWebApplicationException("Format " + fileType + " not valid", HttpStatus.SC_BAD_REQUEST);
                    }
                    boolean isPrimary = file.getPath().equalsIgnoreCase(actualPath);
                    resultMap.put(file.getPath(), ImmutablePair.of(file, new FileDescription(isPrimary)));
                }
            }
        }
        return resultMap;
    }

    @SuppressWarnings("lgtm[java/path-injection]")
    default void createTestParameters(List<String> testParameterPaths, Version workflowVersion, Set<SourceFile> sourceFiles, DescriptorLanguage.FileType fileType, FileDAO fileDAO) {
        for (String path : testParameterPaths) {
            long sourcefileDuplicate = sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType)
                .count();
            if (sourcefileDuplicate == 0) {
                // Sourcefile doesn't exist, add a stub which will have it's content filled on refresh
                SourceFile sourceFile = new SourceFile();
                sourceFile.setPath(path);
                sourceFile.setAbsolutePath(Paths.get(StringUtils.prependIfMissing(workflowVersion.getWorkingDirectory(), "/")).resolve(path).toString()); // lgtm[java/path-injection]
                sourceFile.setType(fileType);

                long id = fileDAO.create(sourceFile);
                SourceFile sourceFileWithId = fileDAO.findById(id);
                workflowVersion.addSourceFile(sourceFileWithId);
            }
        }
    }

    /**
     * Creates a zip file in the tmp dir for the given files
     * @param sourceFiles Set of sourcefiles
     * @param workingDirectory need a working directory to translate relative paths (which we store) to absolute paths
     */
    default void writeStreamAsZip(Set<SourceFile> sourceFiles, OutputStream outputStream, Path workingDirectory) {

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            List<String> paths = new ArrayList<>();

            // If this sourceFile content is null, do not write it.  Empty is acceptable though.
            sourceFiles.removeIf(sourceFile -> sourceFile.getContent() == null);
            // Write each sourcefile
            for (SourceFile sourceFile : sourceFiles) {
                Path resolve = workingDirectory.resolve(sourceFile.getAbsolutePath());
                File file = resolve.toFile();
                String stripStart = removeWorkingDirectory(file.getPath(), file.getName());
                ZipEntry secondaryZipEntry = new ZipEntry(stripStart);

                // Deal with folders
                Path filePath = Paths.get(stripStart).normalize();
                if (filePath.getNameCount() > 1) {
                    String parentPath = filePath.getParent().toString() + "/";
                    if (!paths.contains(parentPath)) {
                        zipOutputStream.putNextEntry(new ZipEntry(parentPath));
                        zipOutputStream.closeEntry();
                        paths.add(parentPath);
                    }
                }
                zipOutputStream.putNextEntry(secondaryZipEntry);
                zipOutputStream.write(sourceFile.getContent().getBytes(Charsets.UTF_8));
            }
        } catch (IOException ex) {
            throw new CustomWebApplicationException("Could not create ZIP file", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    static String generateZipFileName(String path, String versionName) {
        final String pathName = path.replaceAll("/", "-");
        return pathName + '-' + versionName + ".zip";
    }

    static String removeWorkingDirectory(String path, String filename) {
        // remove quirk of working directory, but preserve hidden files
        final int nameIndex = path.lastIndexOf(filename);
        final String pathWithoutName = StringUtils.stripStart(path.substring(0, nameIndex), "./");
        return pathWithoutName + filename;
    }

    /**
     * Checks if the include string (csv) includes some field
     * @param include CSV string
     * @param field Field to query for
     * @return True if include has the given field, false otherwise
     */
    default boolean checkIncludes(String include, String field) {
        String includeString = (include == null ? "" : include);
        ArrayList<String> includeSplit = new ArrayList(Arrays.asList(includeString.split(",")));
        return includeSplit.contains(field);
    }

    default void checkNotFrozen(Version version) {
        if (version.isFrozen()) {
            throw new CustomWebApplicationException(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY, HttpStatus.SC_BAD_REQUEST);
        }
    }

    default SortedSet<SourceFile> getVersionsSourcefiles(Long entryId, Long versionId, List<DescriptorLanguage.FileType> fileTypes, VersionDAO versionDAO) {
        T entry = findEntryById(entryId, Optional.empty());
        Version version = findVersionById(entryId, versionId, versionDAO);
        SortedSet<SourceFile> sourceFiles = version.getSourceFiles();
        if (fileTypes != null && !fileTypes.isEmpty()) {
            sourceFiles = sourceFiles.stream().filter(sourceFile -> fileTypes.contains(sourceFile.getType())).collect(Collectors.toCollection(
                    TreeSet::new));
        }
        return sourceFiles;

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
