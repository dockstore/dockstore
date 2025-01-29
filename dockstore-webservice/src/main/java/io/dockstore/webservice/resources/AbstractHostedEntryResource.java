/*
 *    Copyright 2018 OICR
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
package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.DockstoreWebserviceConfiguration.LimitConfig;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.TopicSelection;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.LambdaUrlChecker;
import io.dockstore.webservice.helpers.LimitHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods to create and edit hosted tool and workflows.
 * Reuse existing methods to GET them, add labels to them, and other operations.
 *
 * @author dyuen
 */
@Api("hosted")
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractHostedEntryResource<T extends Entry<T, U>, U extends Version<U>, W extends EntryDAO<T>, X extends VersionDAO<U>>
        implements AuthenticatedResourceInterface, EntryVersionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHostedEntryResource.class);
    private static final String UPDATED_SOURCEFILES = "Set of updated source files, add files by adding new files with unknown paths, delete files by including them with null content";
    private final FileDAO fileDAO;
    private final UserDAO userDAO;
    private final PermissionsInterface permissionsInterface;
    private final FileFormatDAO fileFormatDAO;
    private final EventDAO eventDAO;
    private final int calculatedEntryLimit;
    private final int calculatedEntryVersionLimit;
    private LambdaUrlChecker checkUrlInterface;

    AbstractHostedEntryResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration config) {
        this.fileFormatDAO = new FileFormatDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.permissionsInterface = permissionsInterface;
        final LimitConfig limitConfig = config.getLimitConfig();
        this.calculatedEntryLimit = MoreObjects.firstNonNull(limitConfig.getWorkflowLimit(), Integer.MAX_VALUE);
        this.calculatedEntryVersionLimit = MoreObjects.firstNonNull(limitConfig.getWorkflowVersionLimit(), Integer.MAX_VALUE);
        final String lambdaUrl = config.getCheckUrlLambdaUrl();
        if (lambdaUrl != null) {
            checkUrlInterface = new LambdaUrlChecker(lambdaUrl);
        }

    }

    /**
     * Convenience method to return a DAO responsible for creating T
     *
     * @return a DAO that handles T
     */
    protected abstract W getEntryDAO();

    /**
     * Convenience method to return a DAO responsible for creating U
     *
     * @return a DAO that handles U
     */
    protected abstract X getVersionDAO();

    public T createHosted(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        String registry, String name, DescriptorLanguage descriptorLanguage, String namespace, String entryName) {

        // check if the user has hit a limit yet
        final long currentCount = getEntryDAO().countAllHosted(user.getId());
        final int limit = user.getHostedEntryCountLimit() != null ? user.getHostedEntryCountLimit() : calculatedEntryLimit;
        if (currentCount >= limit) {
            throw new CustomWebApplicationException("You have " + currentCount + " workflows which is at the current limit of " + limit, HttpStatus.SC_PAYMENT_REQUIRED);
        }

        checkEntryName(entryName); // Check if the entry name is valid
        // Only check type for workflows
        String convertedRegistry = checkRegistry(registry);
        T entry = getEntry(user, convertedRegistry, name, descriptorLanguage, namespace, entryName);
        // manual workflows should always have a manual topic because there is no source control
        entry.setTopicSelection(TopicSelection.MANUAL);
        checkForDuplicatePath(entry);
        long l = getEntryDAO().create(entry);
        return getEntryDAO().findById(l);
    }

    protected abstract void checkForDuplicatePath(T entry);

    /**
     * TODO: ugly, too many strings lead to an easy mix-up of order.
     * @param user User object
     * @param registry Registry of tool (Tools only)
     * @param name Repository name
     * @param descriptorType Type of descriptor (Workflows only)
     * @param namespace Namespace of tool (Tools only)
     * @param entryName Optional entry name
     * @return Newly created entry
     */
    protected abstract T getEntry(User user, String registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName);

    @Override
    public boolean canExamine(User user, Entry entry) {
        return EntryVersionHelper.super.canExamine(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.READ);
    }

    @Override
    public boolean canWrite(User user, Entry entry) {
        return isWritable(entry) && (EntryVersionHelper.super.canWrite(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.WRITE));
    }

    @Override
    public boolean canShare(User user, Entry entry) {
        return EntryVersionHelper.super.canShare(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.SHARE);
    }

    @PATCH
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    @Consumes(MediaType.APPLICATION_JSON)
    public T editHosted(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @Parameter(description = "Entry to modify", name = "entryId", in = ParameterIn.PATH) @PathParam("entryId") Long entryId,
        @ApiParam(value = UPDATED_SOURCEFILES, required = true)
        @Parameter(description = UPDATED_SOURCEFILES, name = "sourceFiles", required = true) Set<SourceFile> sourceFiles) {
        T entry = getEntryDAO().findById(entryId);
        checkNotNullEntry(entry);
        checkCanWrite(user, entry);
        checkHosted(entry);

        checkVersionLimit(user, entry);

        updateUnsetAbsolutePaths(sourceFiles);

        U version = getVersion(entry);
        Set<SourceFile> versionSourceFiles = handleSourceFileMerger(entryId, sourceFiles, entry, version);
        return saveVersion(user, entryId, entry, version, versionSourceFiles, Optional.empty());
    }

    protected void checkVersionLimit(@Auth @ApiParam(hidden = true) User user, T entry) {
        // check if the user has hit a limit yet
        final long currentCount = entry.getWorkflowVersions().size();
        final int limit = user.getHostedEntryVersionsLimit() != null ? user.getHostedEntryVersionsLimit() : calculatedEntryVersionLimit;
        if (currentCount >= limit) {
            throw new CustomWebApplicationException("You have " + currentCount + " workflow versions which is at the current limit of " + limit, HttpStatus.SC_PAYMENT_REQUIRED);
        }
    }

    /**
     * Saves a version of a hosted entry.
     *
     * This code was extracted from the editHosted method for reuse with with the zip posting functionality. For zips, the
     * mainDescriptor parameter was added.
     *
     * Until the zip support, hosted entries only supported a workflow path of /Dockstore.[wdl|cwl]. With zips, the user can
     * specify any workflow path. The mainDescriptor is used to override the default /Dockstore.[wdl\cwl] within a version.
     *
     *
     * @param user
     * @param entryId
     * @param entry
     * @param version
     * @param versionSourceFiles
     * @param mainDescriptor the path of the main descriptor if different than the workflow default
     * @return
     */
    T saveVersion(User user, Long entryId, T entry, U version, Set<SourceFile> versionSourceFiles, Optional<SourceFile> mainDescriptor) {
        final U validatedVersion = versionValidation(version, entry, mainDescriptor);

        boolean isValidVersion = isValidVersion(validatedVersion);
        if (!isValidVersion) {
            String fallbackMessage = "Your edited files are invalid. No new version was created. Please check your syntax and try again.";
            String validationMessages = createValidationMessages(validatedVersion);
            validationMessages = (validationMessages != null && !validationMessages.isEmpty()) ? validationMessages : fallbackMessage;
            throw new CustomWebApplicationException(validationMessages, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }

        String invalidFileNames = String.join(",", invalidFileNames(version));
        if (!invalidFileNames.isEmpty()) {
            String message = "Files must have a name. Unable to save new version due to the following files: "
                + invalidFileNames;
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }

        // Check the version to see if it exceeds any limits.
        LimitHelper.checkVersion(validatedVersion);

        validatedVersion.setValid(true); // Hosted entry versions must be valid to save
        validatedVersion.setVersionEditor(user);
        populateMetadata(versionSourceFiles, entry, validatedVersion);
        validatedVersion.setParent(entry);
        long l = getVersionDAO().create(validatedVersion);
        entry.getWorkflowVersions().add(getVersionDAO().findById(l));
        // Only set if the default version isn't already there
        if (entry.getDefaultVersion() == null) {
            entry.checkAndSetDefaultVersion(validatedVersion.getName());
        }
        // Set entry-level metadata to this latest version
        // TODO: handle when latest version is removed
        entry.setActualDefaultVersion(validatedVersion);
        entry.syncMetadataWithDefault();
        FileFormatHelper.updateFileFormats(entry, entry.getWorkflowVersions(), fileFormatDAO, true);

        // TODO: Not setting lastModified for hosted tools now because we plan to get rid of the lastmodified column in Tool table in the future.
        if (validatedVersion instanceof WorkflowVersion workflowVersion) {
            entry.setLastModified(workflowVersion.getLastModified());
            if (checkUrlInterface != null) {
                Workflow workflow = (Workflow) entry; // It's a workflow version, so it has to be a workflow
                AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, checkUrlInterface, workflow.getDescriptorType());
            }
        }
        updateBlacklistedVersionNames(entry, version);
        userDAO.clearCache();
        T newTool = getEntryDAO().findById(entryId);
        PublicStateManager.getInstance().handleIndexUpdate(newTool, StateManagerMode.UPDATE);
        this.eventDAO.createAddTagToEntryEvent(Optional.of(user), newTool, version);
        return newTool;
    }

    private void updateBlacklistedVersionNames(T entry, U version) {
        entry.getBlacklistedVersionNames().add(version.getName());
    }

    /**
     * For all source files whose absolutePath is not set, set the absolutePath to the path.
     *
     * The absolutePath may not be null in the database, but it is not set when the UI invokes
     * the Webservice API.
     *
     * @param sourceFiles
     */
    private void updateUnsetAbsolutePaths(Set<SourceFile> sourceFiles) {
        sourceFiles.forEach(sourceFile -> {
            if (sourceFile.getAbsolutePath() == null) {
                sourceFile.setAbsolutePath(sourceFile.getPath());
            }
        });
    }

    /**
     * Prints out all of the invalid validations
     * Used for returning error messages on attempting to save
     * @param version version of interest
     * @return String containing all invalid validation messages
     */
    protected String createValidationMessages(U version) {
        StringBuilder result = new StringBuilder();
        result.append("Unable to save the new version due to the following error(s): ");
        Gson g = new Gson();
        for (Validation versionValidation : version.getValidations()) {
            if (!versionValidation.isValid() && versionValidation.getMessage() != null) {
                Map<String, String> message = g.fromJson(versionValidation.getMessage(), HashMap.class);
                for (Map.Entry<String, String> entry : message.entrySet()) {
                    result.append(entry.getKey() + ": " + entry.getValue() + " ");
                }
            }
        }

        return result.toString();
    }

    protected List<String> invalidFileNames(U version) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        List<String> invalidFileNames = new ArrayList<>();

        sourceFiles.stream().forEach(sourceFile -> {
            if (sourceFile.getPath().endsWith("/")) {
                invalidFileNames.add(sourceFile.getPath());
            }
        });

        return invalidFileNames;
    }

    /**
     * Checks if the given version is valid based on existing version validations
     * @param version Version to validate
     * @return True if valid version, false otherwise
     */
    protected abstract boolean isValidVersion(U version);

    protected abstract void populateMetadata(Set<SourceFile> sourceFiles, T entry, U version);

    /**
     * Will update the version with validation information
     * Note: There is one validation entry for each sourcefile type. This is true for test parameter files too.
     * @param version Version to validate
     * @param entry Entry for the version
     * @param mainDescriptor the main descriptor if different than the default /Dockstore.wdl or /Dockstore.cwl
     * @return Version with updated validation information
     */
    protected abstract U versionValidation(U version, T entry, Optional<SourceFile> mainDescriptor);

    protected void checkHosted(T entry) {
        if (entry instanceof Tool) {
            if (((Tool)entry).getMode() != ToolMode.HOSTED) {
                throw new CustomWebApplicationException("cannot modify non-hosted entries this way", HttpStatus.SC_BAD_REQUEST);
            }
        } else if (entry instanceof Workflow) {
            if (((Workflow)entry).getMode() != WorkflowMode.HOSTED) {
                throw new CustomWebApplicationException("cannot modify non-hosted entries this way", HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    /**
     * Check that the registry is a valid registry and return the registry object
     * @param registry
     * @return
     */
    protected abstract String checkRegistry(String registry);

    /**
     * Check that the entry name is valid
     * @param entryName
     */
    protected abstract void checkEntryName(String entryName);

    /**
     * Create new version of a workflow or tag of a tool
     * @param entry the parent for the new version
     * @return the new version
     */
    protected abstract U getVersion(T entry);

    @DELETE
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully deleted hosted entry version", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    public T deleteHostedVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "version", required = true) @QueryParam("version") String version) {
        T entry = getEntryDAO().findById(entryId);
        checkNotNullEntry(entry);
        checkCanWrite(user, entry);
        checkHosted(entry);

        Optional<U> deleteVersion =  entry.getWorkflowVersions().stream().filter(v -> Objects.equals(v.getName(), version)).findFirst();
        if (deleteVersion.isEmpty()) {
            throw new CustomWebApplicationException("Cannot find version: " + version + " to delete", HttpStatus.SC_NOT_FOUND);
        }

        if (deleteVersion.get().isFrozen()) {
            throw new CustomWebApplicationException("Cannot delete a snapshotted version.", HttpStatus.SC_BAD_REQUEST);
        }

        // If the version that's about to be deleted is the default version, unset it
        if (entry.getActualDefaultVersion() != null && entry.getActualDefaultVersion().getName().equals(version)) {
            Optional<U> max = entry.getWorkflowVersions().stream().filter(v -> !Objects.equals(v.getName(), version))
                    .max(Comparator.comparingLong(ver -> ver.getDate().getTime()));
            entry.setActualDefaultVersion(max.orElse(null));
        }
        entry.getWorkflowVersions().removeIf(v -> Objects.equals(v.getName(), version));
        // Deleting a version could completely remove a input/output file format
        FileFormatHelper.updateEntryLevelFileFormats(entry);
        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);
        return entry;
    }

    private Set<SourceFile> handleSourceFileMerger(Long entryId, Set<SourceFile> sourceFiles, T entry, U tag) {
        Set<U> versions = entry.getWorkflowVersions();
        Map<String, SourceFile> map = new HashMap<>();
        tag.setName(calculateNextVersionName(versions, entry));

        if (versions.size() > 0) {
            // get the last one and modify files accordingly
            U versionWithTheLargestName = versionWithLargestName(versions);
            // carry over old files
            versionWithTheLargestName.getSourceFiles().forEach(v -> {
                SourceFile newFile = v.duplicate();
                map.put(newFile.getPath(), newFile);
            });

            boolean changed = false;
            // mutate sourcefiles accordingly
            // 1) matching filenames are updated with the new content
            // 2) empty files are deleted
            // 3) new files are created
            for (SourceFile file : sourceFiles) {
                // ignore IDs if they were populated
                file.setId(0);

                if (map.containsKey(file.getPath())) {
                    if (file.getContent() != null) {
                        // case 1)
                        final SourceFile sourceFile = map.get(file.getPath());
                        if (!sourceFile.getContent().equals(file.getContent())) {
                            sourceFile.updateFrom(file);
                            changed = true;
                        }
                    } else {
                        // case 2)
                        map.remove(file.getPath());
                        LOG.info("deleted " + file.getPath() + " for new revision of " + entryId);
                        changed = true;
                    }
                } else {
                    // case 3
                    map.put(file.getPath(), file);
                    changed = true;
                }
            }

            if (!changed) {
                LOG.info("aborting change, there were no differences detected for new revision of " + entryId);
                throw new CustomWebApplicationException("no changes detected", HttpStatus.SC_NO_CONTENT);
            }
        } else {
            // for brand new hosted tools
            sourceFiles.forEach(f -> map.put(f.getPath(), f));
        }
        persistSourceFiles(tag, map.values());

        return tag.getSourceFiles();
    }

    void persistSourceFiles(U tag, Collection<SourceFile> sourceFiles) {
        // create everything still in the map
        for (SourceFile e : sourceFiles) {
            long l = fileDAO.create(e);
            tag.getSourceFiles().add(fileDAO.findById(l));
        }
    }

    /**
     * Calculates the next version name. Currently assumes versions are always stringified numbers, and returns
     * the highest number + 1 as a string. Need to update when we support arbitrary names for the version.
     * @param versions  The existing list of versions
     * @return          The version name of the next version
     */
    String calculateNextVersionName(Set<U> versions, T entry) {
        Set<String> blacklistedVersionNames = entry.getBlacklistedVersionNames();
        Set<String> currentVersionNames = versions.stream().map(Version::getName).collect(Collectors.toSet());
        Set<String> combinedVersionNames = Stream.concat(blacklistedVersionNames.stream(), currentVersionNames.stream()).collect(Collectors.toSet());
        if (combinedVersionNames.isEmpty()) {
            return "1";
        } else {
            Set<Integer> versionNamesAsIntegers = combinedVersionNames.stream().map(Integer::parseInt).collect(Collectors.toSet());
            Integer max = Collections.max(versionNamesAsIntegers);
            return String.valueOf(max + 1);
        }
    }

    private U versionWithLargestName(Set<U> versions) {
        Comparator<Version> comp = Comparator.comparingInt(p -> Integer.parseInt(p.getName()));
        // there should always be a max with size() > 0
        return versions.stream().max(comp).orElseThrow(RuntimeException::new);
    }

}
