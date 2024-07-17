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
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
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

public class GitHubFileTree implements FileTree {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubFileTree.class);

    private final GitHubSourceCodeRepo gitHubSourceCodeRepo;
    private final String repository;
    private final String ref;
    private final ZipFile zipFile;
    private final Map<String, ZipArchiveEntry> pathToEntry;

    public GitHubFileTree(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String ref) {
        this.gitHubSourceCodeRepo = gitHubSourceCodeRepo;
        this.repository = repository;
        this.ref = ref;
        // Read the Zip contents and create an in-memory ZipFile.
        byte[] zipBytes = gitHubSourceCodeRepo.readZip(repository, ref);
        LOG.info("downloaded Zip of GitHub repository: %n bytes".formatted(zipBytes.length));
        SeekableByteChannel zipChannel = new SeekableInMemoryByteChannel(zipBytes);
        try {
            zipFile = new ZipFile(zipChannel);
        } catch (IOException e) {
            LOG.error("could not read zip archive of GitHub repository", e);
            throw new CustomWebApplicationException("could not read GitHub repository", HttpStatus.SC_BAD_REQUEST);
        }
        // Create a Map of absolute paths to Zip file entries.
        pathToEntry = Collections.list(zipFile.getEntries()).stream()
            .filter(entry -> !entry.isDirectory() && !entry.isUnixSymlink())
            .collect(Collectors.toMap(this::pathFromEntry, entry -> entry, (valueA, valueB) -> valueA));
    }

    @Override
    public String readFile(String path) {
        ZipArchiveEntry entry = pathToEntry.get(path);
        if (entry != null) {
            try (InputStream in = zipFile.getInputStream(entry)) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.error("could not extract file from ZipArchiveEntry: " + cleanString(entry), e);
                throw new CustomWebApplicationException("could not read file from GitHub repository", HttpStatus.SC_BAD_REQUEST);
            }
        }
        // Fall back to the old-style way of reading files, to handle symlinks and submodules.
        return gitHubSourceCodeRepo.readFile(path, repository, ref);
    }

    @Override
    public List<String> listFiles(String path) {
        return gitHubSourceCodeRepo.listFiles(path, repository, ref);
    }

    @Override
    public List<String> listPaths() {
        return new ArrayList<>(pathToEntry.keySet());
    }

    /**
     * Computes the actual file path from the information in a specified Zip entry by stripping off the leading path component.
     * In a GitHub Zip archive, all paths begin with a path component that is formed from the repo/ref information.
     */
    private String pathFromEntry(ZipArchiveEntry entry) {
        String[] parts = entry.getName().split("/", 2);
        if (parts.length != 2) {
            LOG.error("could not parse ZipArchiveEntry: " + cleanString(entry));
            throw new CustomWebApplicationException("could not read GitHub repository", HttpStatus.SC_BAD_REQUEST);
        }
        return "/" + parts[1];
    }

    private String cleanString(Object obj) {
        if (obj != null) {
            return Utilities.cleanForLogging(obj.toString());
        } else {
            return "null";
        }
    }
}
