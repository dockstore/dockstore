/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstracts the file tree corresponding to a reference to a GitHub repository.
 *
 * Upon initialization, this implementation retrieves a Zipball of the ref/repo
 * file tree and constructs a path-to-ZipArchiveEntry map corresponding to normal
 * (non-symlink) files.  Sebsequently, on demand, the map is used to extract file
 * contents and a list of paths.
 *
 * The zip file and path-to-entry map do not include submodule files or paths
 * that contain a symlink component.  If `readFile` can't find a path in the map,
 * we attempt to read the file via `GitHubSourceCodeRepo.readFile`, which
 * supports symlinks and submodules.
 */
public class ZipGitHubFileTree implements FileTree {

    private static final Logger LOG = LoggerFactory.getLogger(ZipGitHubFileTree.class);

    private final GitHubSourceCodeRepo gitHubSourceCodeRepo;
    private final String repository;
    private final String ref;
    private final ZipFile zipFile;
    private final Map<Path, ZipArchiveEntry> pathToEntry;

    public ZipGitHubFileTree(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String ref) {
        this.gitHubSourceCodeRepo = gitHubSourceCodeRepo;
        this.repository = repository;
        this.ref = ref;
        // Read the Zip contents and create an in-memory ZipFile.
        byte[] zipBytes = gitHubSourceCodeRepo.readZip(repository, ref);
        LOG.info("downloaded Zip of GitHub repository: %n bytes".formatted(zipBytes.length));
        SeekableInMemoryByteChannel zipChannel = new SeekableInMemoryByteChannel(zipBytes);
        try {
            zipFile = new ZipFile(zipChannel);
        } catch (IOException e) {
            LOG.error("could not read zip archive of GitHub repository", e);
            throw new CustomWebApplicationException("could not read GitHub repository", HttpStatus.SC_BAD_REQUEST);
        }
        // Create a Map of absolute paths to Zip file entries for normal (non-symlink) files.
        pathToEntry = Collections.list(zipFile.getEntries()).stream()
            .filter(entry -> !entry.isDirectory() && !entry.isUnixSymlink())
            .collect(Collectors.toMap(this::pathFromEntry, entry -> entry, (valueA, valueB) -> valueA));
    }

    @Override
    public String readFile(Path filePath) {
        // If specified file is included in the ZipFile, uncompress its content, convert it to a string, and return it.
        ZipArchiveEntry entry = pathToEntry.get(filePath);
        if (entry != null) {
            try (InputStream in = zipFile.getInputStream(entry)) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.error("could not extract file from ZipArchiveEntry: " + stringifyAndClean(entry), e);
                throw new CustomWebApplicationException("could not read file from GitHub repository", HttpStatus.SC_BAD_REQUEST);
            }
        }
        // Otherwise, use our existing GitHub API code [to handle symlinks and submodules].
        return gitHubSourceCodeRepo.readFile(filePath.toString(), repository, ref);
    }

    @Override
    public List<String> listFiles(Path dirPath) {
        return gitHubSourceCodeRepo.listFiles(dirPath.toString(), repository, ref);
    }

    @Override
    public List<Path> listPaths() {
        return new ArrayList<>(pathToEntry.keySet());
    }

    /**
     * Computes a file's absolute path, relative to the repo root, from the information in a specified Zip entry.
     * In a GitHub Zipball, all file paths are prepended with a path component formed from the repo/ref information, which this code strips off.
     */
    private Path pathFromEntry(ZipArchiveEntry entry) {
        String[] parts = entry.getName().split("/", 2);
        if (parts.length != 2) {
            LOG.error("could not parse ZipArchiveEntry: " + stringifyAndClean(entry));
            throw new CustomWebApplicationException("could not read GitHub repository", HttpStatus.SC_BAD_REQUEST);
        }
        return Paths.get("/", parts[1]);
    }

    private String stringifyAndClean(Object obj) {
        if (obj != null) {
            return Utilities.cleanForLogging(obj.toString());
        } else {
            return "null";
        }
    }
}
