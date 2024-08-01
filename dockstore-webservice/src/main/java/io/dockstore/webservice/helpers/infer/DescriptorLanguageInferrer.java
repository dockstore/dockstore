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

package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.FileTree;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the infrastructure for Inferrers that handle a particular descriptor language.
 * See the comments in the `infer` method for an overview of the technique used.
 * Concrete implementations can/should override various methods to implement language-specific behavior,
 * most typically the `determineType`, `determineName`, and `determineSubclass` methods.
 */
public abstract class DescriptorLanguageInferrer implements Inferrer {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptorLanguageInferrer.class);
    /**
     * Regular expression that matches the non-space characters that can separate paths from other content.
     */
    protected static final Pattern NON_SPACE_SEPARATORS = Pattern.compile("[!-,:-@\\[-^`{-~]+");
    /**
     * The minimum length of a string that we will consider as a potential referenced path.
     */
    private static final int MIN_REFERENCED_PATH_LENGTH = 4;
    private static final String SPACE = " ";
    private static final String DOT = ".";
    private final DescriptorLanguage language;

    public DescriptorLanguageInferrer(DescriptorLanguage language) {
        this.language = language;
    }

    @Override
    public List<Entry> infer(FileTree fileTree) {
        // Get a list of paths that are probably descriptors.
        List<String> paths = fileTree.listPaths().stream().filter(this::isDescriptorPath).toList();
        // Remove paths that likely correspond to tests, subtasks, etc.
        paths = removeNonPrimaryPaths(paths);
        // Remove paths that are referenced by other descriptors.
        paths = removeReferencedPaths(fileTree, paths);
        // For each path that remains, attempt to infer the entries.
        return paths.stream().flatMap(path -> infer(fileTree, path).stream()).toList();
    }

    public List<Entry> infer(FileTree fileTree, String path) {
        if (isDescriptorPath(path)) {
            EntryType type = determineType(fileTree, path);
            if (type != null) {
                String name = determineName(fileTree, path);
                DescriptorLanguageSubclass subclass = determineSubclass(fileTree, path, type);
                return List.of(new Entry(type, language, subclass, path, name));
            }
        }
        return List.of();
    }

    protected abstract boolean isDescriptorPath(String path);

    /**
     * Remove the paths that probably don't represent primary descriptors
     * that the user would want to reference in their .dockstore.yml
     */
    protected List<String> removeNonPrimaryPaths(List<String> paths) {
        return paths.stream().filter(path -> {
            String lower = path.toLowerCase();
            return !(lower.contains("test") || lower.contains("archive") || lower.contains("debug"));
        }).toList();
    }

    protected List<String> removeReferencedPaths(FileTree fileTree, List<String> paths) {
        Set<String> unreferencedPaths = new LinkedHashSet<>(paths);
        paths.forEach(path -> unreferencedPaths.removeAll(determineReferencedPaths(fileTree, path)));
        return toList(unreferencedPaths);
    }

    protected List<String> determineReferencedPaths(FileTree fileTree, String srcPath) {
        String content = removeComments(readFile(fileTree, srcPath));
        List<String> paths = new ArrayList<>();
        for (String chunk: NON_SPACE_SEPARATORS.split(content)) {
            if (isReferencedPath(chunk)) {
                toAbsolutePath(srcPath, chunk).ifPresent(paths::add);
            }
            for (String subChunk: chunk.split(SPACE)) {
                if (isReferencedPath(subChunk)) {
                    toAbsolutePath(srcPath, subChunk).ifPresent(paths::add);
                }
            }
        }
        return paths;
    }

    protected boolean isReferencedPath(String possiblePath) {
        return possiblePath.length() >= MIN_REFERENCED_PATH_LENGTH && possiblePath.contains(DOT) && isDescriptorPath(possiblePath);
    }

    private Optional<String> toAbsolutePath(String currentPath, String relativeOrAbsolutePath) {
        try {
            return Optional.of(Paths.get(currentPath).resolve(relativeOrAbsolutePath).normalize().toString());
        } catch (InvalidPathException e) {
            // If either path was invalid, ignore and continue.
            return Optional.empty();
        }
    }

    protected EntryType determineType(FileTree fileTree, String path) {
        Set<EntryType> entryTypes = language.getEntryTypes();
        // If this descriptor language only supports one type of entry, return it.
        if (entryTypes.size() == 1) {
            return entryTypes.iterator().next();
        }
        // Otherwise, this method must be overriden.
        throw new UnsupportedOperationException();
    }

    protected String determineName(FileTree fileTree, String path) {
        return null;
    }

    protected DescriptorLanguageSubclass determineSubclass(FileTree fileTree, String path, EntryType type) {
        Set<DescriptorLanguageSubclass> subclasses = DescriptorLanguageSubclass.valuesForEntryType(type);
        // If this descriptor language only supports one type of subclass, return it.
        if (subclasses.size() == 1) {
            return subclasses.iterator().next();
        }
        // Otherwise, this method must be overriden.
        throw new UnsupportedOperationException();
    }

    protected String readFile(FileTree tree, String path) {
        String content = tree.readFile(path);
        if (content == null) {
            throw new CustomWebApplicationException("could not find file", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return content;
    }

    protected String removeComments(String content) {
        return content.replaceAll("#.*", "");
    }

    protected boolean lineContainsRegex(String regex, String s) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(s).find();
    }

    protected String groupFromLineContainingRegex(String regex, int groupIndex, String s) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(s);
        if (matcher.find()) {
            return matcher.group(groupIndex);
        } else {
            return null;
        }
    }

    protected <T> List<T> toList(Collection<? extends T> values) {
        return new ArrayList<T>(values);
    }
}
