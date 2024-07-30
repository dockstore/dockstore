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
import java.util.Arrays;
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
    private static final Pattern POSSIBLE_PATH = Pattern.compile("[./]*+[a-zA-Z0-9/_-]++\\.[a-zA-Z0-9]++");
    private static final int MAX_REFERENCED_PATH_LENGTH = 64;
    private static final Pattern NON_PATH_CHARACTERS = Pattern.compile("[^.a-zA-Z0-9/_-]");

    private final DescriptorLanguage language;

    public DescriptorLanguageInferrer(DescriptorLanguage language) {
        this.language = language;
    }

    @Override
    public List<Entry> infer(FileTree fileTree) {
        // Get a list of paths that are probably descriptors.
        List<String> paths = fileTree.listPaths().stream().filter(this::isDescriptorPath).toList();
        // Remove paths that likely correspond to tests, subtasks, etc.
        paths = removeTestPaths(paths);
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

    protected List<String> removeTestPaths(List<String> paths) {
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
        String[] chunks = NON_PATH_CHARACTERS.split(content);
        return Arrays.stream(chunks)
            .filter(chunk -> chunk.length() <= MAX_REFERENCED_PATH_LENGTH)
            .filter(chunk -> POSSIBLE_PATH.matcher(chunk).matches())
            .filter(this::isDescriptorPath)
            .map(foundPath -> toAbsolutePath(srcPath, foundPath))
            .flatMap(Optional::stream)
            .toList();
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
