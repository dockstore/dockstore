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
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.FileTree;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class InferrerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InferrerHelper.class);

    /**
     * Infers the details of entries present in the specified file tree, using the Inferrers returned by #getInferrers.
     * @return a list of inferred entries
     */
    public List<Inferrer.Entry> infer(FileTree fileTree) {
        return getInferrers().stream().flatMap(inferrer -> inferrer.infer(fileTree).stream()).toList();
    }

    /**
     * Produces a "standard" list of inferrers.
     */
    public List<Inferrer> getInferrers() {
        // Currently, return an Inferrer for each supported descriptor language.
        // Later, if we add other Inferrers, to support a particular manifest file format, for example, we can include them here.
        return Arrays.stream(DescriptorLanguage.values()).map(this::getInferrer).filter(Objects::nonNull).toList();
    }

    /**
     * Create an Inferrer for the specified descriptor language.
     * @returns an Inferrer, or null if the language is not supported
     */
    public Inferrer getInferrer(DescriptorLanguage language) {
        switch (language) {
        case CWL:
            return new DescriptorLanguageInferrer(DescriptorLanguage.CWL) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".cwl");
                }
                @Override
                protected EntryType determineType(FileTree fileTree, Path path) {
                    String content = readFile(fileTree, path);
                    if (lineContainsRegex("^class:\\s*Workflow", content)) {
                        return EntryType.WORKFLOW;
                    }
                    if (lineContainsRegex("^class:\\s*CommandLineTool", content)) {
                        return EntryType.APPTOOL;
                    }
                    return null;
                }
            };
        case WDL:
            return new DescriptorLanguageInferrer(DescriptorLanguage.WDL) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".wdl");
                }
                @Override
                protected EntryType determineType(FileTree fileTree, Path path) {
                    String content = readFile(fileTree, path);
                    if (lineContainsRegex("^workflow\\s", content)) {
                        return EntryType.WORKFLOW;
                    }
                    return null;
                }
                @Override
                protected String determineName(FileTree fileTree, Path path) {
                    String content = readFile(fileTree, path);
                    return groupFromLineContainingRegex("^workflow\\s+(\\S+)\\s", 1, content);
                }
            };
        case NEXTFLOW:
            return new DescriptorLanguageInferrer(DescriptorLanguage.NEXTFLOW) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith("/nextflow.config");
                }
                @Override
                protected List<Path> determineReferencedPaths(FileTree fileTree, Path path) {
                    return List.of();
                }
            };
        case GXFORMAT2:
            return new DescriptorLanguageInferrer(DescriptorLanguage.GXFORMAT2) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ga");
                }
            };
        case SMK:
            return new DescriptorLanguageInferrer(DescriptorLanguage.SMK) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".snakemake-workflow-catalog.yml");
                }
            };
        case JUPYTER:
            return new DescriptorLanguageInferrer(DescriptorLanguage.JUPYTER) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ipynb");
                }
                @Override
                protected List<Path> determineReferencedPaths(FileTree fileTree, Path path) {
                    return List.of();
                }
                @Override
                protected DescriptorLanguageSubclass determineSubclass(FileTree fileTree, Path path, EntryType type) {
                    String content = readFile(fileTree, path).toLowerCase();
                    // Look for some json that sets the "language" field to one of the legal values.
                    for (DescriptorLanguageSubclass subclass: DescriptorLanguageSubclass.valuesForEntryType(type)) {
                        String regex = "\"language\":\\s*\"%s\"".formatted(subclass.getShortName());
                        if (Pattern.compile(regex).matcher(content).find()) {
                            return subclass;
                        }
                    }
                    return DescriptorLanguageSubclass.PYTHON;
                }
            };
        case SWL:
        case SERVICE:
            return null;
        default:
            // Log and throw, which should make the webservice fail the tests if we add a new descriptor language and forget to adjust this method.
            String message = "no mapping from descriptor language to inferrer";
            LOG.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Tweaks the specified entries so that that they have unique names and no duplicate names or paths.
     */
    public List<Inferrer.Entry> refine(List<Inferrer.Entry> entries) {
        return ensureUniqueNames(legalizeNames(setMissingNames(removeDuplicatePaths(entries))));
    }

    private List<Inferrer.Entry> removeDuplicatePaths(List<Inferrer.Entry> entries) {
        Set<Path> paths = new HashSet<>();
        return entries.stream().filter(entry -> paths.add(entry.path())).toList();
    }

    private List<Inferrer.Entry> setMissingNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(this::setMissingName).toList();
    }

    private Inferrer.Entry setMissingName(Inferrer.Entry entry) {
        return entry.changeName(StringUtils.firstNonEmpty(entry.name(), nameFromPath(entry.path()), nameFromType(entry.type())));
    }

    private List<Inferrer.Entry> legalizeNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(this::legalizeName).toList();
    }

    private Inferrer.Entry legalizeName(Inferrer.Entry entry) {
        String legalName = collapseHyphensAndUnderscores(trimHyphensAndUnderscores(removeNonNameCharacters(entry.name())));
        return entry.changeName(StringUtils.firstNonEmpty(legalName, nameFromType(entry.type())));
    }

    private String removeNonNameCharacters(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private String trimHyphensAndUnderscores(String s) {
        return StringUtils.stripEnd(StringUtils.stripStart(s, "_-"), "_-");
    }

    private String collapseHyphensAndUnderscores(String s) {
        return s.replaceAll("([_-])[_-]++", "$1");
    }

    private List<Inferrer.Entry> ensureUniqueNames(List<Inferrer.Entry> entries) {
        // Compute the set of duplicate names.
        Set<String> names = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        entries.forEach(entry -> {
            String name = entry.name();
            if (names.contains(name)) {
                duplicates.add(name);
            }
            names.add(name);
        });

        // For each name, compute a new name if the name is either:
        //   a) in the set of duplicate names.
        //   b) has already been taken.
        // Create new names by appending an underscore and the value of a counter to the
        // original name.  For example, given duplicate name "a", we would try the names
        // "a_1", "a_2", "a_3", ...
        // Currently, a single counter is used across all names.
        // In a perfect world, there would be a counter per orginal name, but this code is
        // already hard to evaluate from a "does it always halt" point-of-view, so let's
        // keep it simple for now.
        MutableLong counter = new MutableLong(1);
        Set<String> taken = new HashSet<>();
        return entries.stream().map(entry -> {
            String name = entry.name();
            if (duplicates.contains(name) || taken.contains(name)) {
                String newName;
                do {
                    newName = name + "_" + counter.getAndIncrement();
                } while (taken.contains(newName));
                name = newName;
            }
            taken.add(name);
            return entry.changeName(name);
        }).toList();
    }

    private String nameFromPath(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String[] nameParts = fileName.toString().split("\\.");
        if (nameParts.length == 0) {
            return null;
        }
        return nameParts[0];
    }

    private String nameFromType(EntryType type) {
        return type.getTerm();
    }

    /**
     * Creates a .dockstore.yml file from the specified inferred entries.
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public String toDockstoreYaml(List<Inferrer.Entry> entries) {
        // "Refine" the entries to fix issues like missing, duplicate, or illegal names.
        entries = refine(entries);
        // Construct map that contains an abstract representation of the .dockstore.yml.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", new BigDecimal("1.2"));
        putEntries(map, "tools", entries, EntryType.APPTOOL);
        putEntries(map, "workflows", entries, EntryType.WORKFLOW);
        putEntries(map, "notebooks", entries, EntryType.NOTEBOOK);
        // Convert the abstract representation to a yaml string.
        StringWriter writer = new StringWriter();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setIndent(4);
        dumperOptions.setIndicatorIndent(2);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        new Yaml(dumperOptions).dump(map, writer);
        String dockstoreYaml = writer.toString();
        // Parse to make sure we didn't generate an invalid .dockstore.yml
        parseDockstoreYaml(dockstoreYaml);
        return dockstoreYaml;
    }

    private void putEntries(Map<String, Object> map, String fieldName, List<Inferrer.Entry> entries, EntryType type) {
        List<Map<String, Object>> maps = entries.stream()
            .filter(entry -> entry.type() == type)
            .map(this::entryToMap)
            .toList();
        if (!maps.isEmpty()) {
            map.put(fieldName, maps);
        }
    }

    private Map<String, Object> entryToMap(Inferrer.Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        putString(map, "name", entry.name());
        if (entry.type() == EntryType.NOTEBOOK) {
            putString(map, "format", entry.language());
            putString(map, "language", entry.subclass());
            putString(map, "path", entry.path());
        } else {
            putString(map, "subclass", entry.language());
            putString(map, "primaryDescriptorPath", entry.path());
        }
        return map;
    }

    private void putString(Map<String, Object> map, String fieldName, Object value) {
        if (value != null) {
            map.put(fieldName, value.toString());
        }
    }

    private void parseDockstoreYaml(String dockstoreYaml) {
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYaml, true);
        } catch (Exception e) {
            String message = "error creating .dockstore.yml";
            LOG.error(message, e);
            throw new CustomWebApplicationException(message, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
