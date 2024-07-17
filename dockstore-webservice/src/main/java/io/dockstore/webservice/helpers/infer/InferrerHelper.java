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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class InferrerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InferrerHelper.class);

    public List<Inferrer.Entry> infer(FileTree fileTree) {
        return getInferrers().stream().flatMap(inferrer -> inferrer.infer(fileTree).stream()).toList();
    }

    public List<Inferrer> getInferrers() {
        return Arrays.stream(DescriptorLanguage.values()).map(this::getInferrer).filter(Objects::nonNull).toList();
    }

    public Inferrer getInferrer(DescriptorLanguage language) {
        switch (language) {
        case CWL:
            return new BasicInferrer(DescriptorLanguage.CWL) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".cwl");
                }
                @Override
                protected EntryType determineType(FileTree fileTree, String path) {
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
            return new BasicInferrer(DescriptorLanguage.WDL) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".wdl");
                }
                @Override
                protected EntryType determineType(FileTree fileTree, String path) {
                    String content = readFile(fileTree, path);
                    if (lineContainsRegex("^workflow\\s", content)) {
                        return EntryType.WORKFLOW;
                    }
                    return null;
                }
                @Override
                protected String determineName(FileTree fileTree, String path) {
                    String content = readFile(fileTree, path);
                    return groupFromLineContainingRegex("^workflow\\s+(\\S+)\\s", 1, content);
                }
            };
        case NEXTFLOW:
            return new BasicInferrer(DescriptorLanguage.NEXTFLOW) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith("/nextflow.config");
                }
                @Override
                protected List<String> determineReferencedPaths(FileTree fileTree, String path) {
                    return List.of();
                }
            };
        case GXFORMAT2:
            return new BasicInferrer(DescriptorLanguage.GXFORMAT2) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ga");
                }
            };
        case JUPYTER:
            return new BasicInferrer(DescriptorLanguage.JUPYTER) {
                @Override
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ipynb");
                }
                @Override
                protected List<String> determineReferencedPaths(FileTree fileTree, String path) {
                    return List.of();
                }
                @Override
                protected DescriptorLanguageSubclass determineSubclass(FileTree fileTree, String path, EntryType type) {
                    String content = readFile(fileTree, path).toLowerCase();
                    // Look for some json that sets the "language" field to one of the legal values.
                    for (DescriptorLanguageSubclass subclass: DescriptorLanguageSubclass.valuesForEntryType(type)) {
                        String regex = "\"language\":\\s*\"%s\"".formatted(subclass.getShortName());
                        if (Pattern.compile(regex).matcher(content).find()) {
                            return subclass;
                        }
                    }
                    return null;
                }
            };
        case SMK:
        case SWL:
        case SERVICE:
            return null;
        default:
            String message = "no mapping from descriptor language to inferrer";
            LOG.error(message);
            throw new IllegalStateException(message);
        }
    }

    public List<Inferrer.Entry> refine(List<Inferrer.Entry> entries) {
        return ensureUniqueNames(legalizeNames(setMissingNames(removeDuplicatePaths(entries))));
    }

    private List<Inferrer.Entry> removeDuplicatePaths(List<Inferrer.Entry> entries) {
        Set<String> paths = new HashSet<>();
        return entries.stream().filter(entry -> paths.add(entry.path())).toList();
    }

    private List<Inferrer.Entry> setMissingNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(this::setMissingName).toList();
    }

    private Inferrer.Entry setMissingName(Inferrer.Entry entry) {
        return entry.changeName(StringUtils.firstNonEmpty(entry.name(), nameFromPath(entry.path()), defaultName(entry)));
    }

    private String nameFromPath(String path) {
        String[] pathParts = path.split("/");
        if (pathParts.length == 0) {
            return null;
        }
        String[] nameParts = pathParts[pathParts.length - 1].split("\\.");
        if (nameParts.length == 0) {
            return null;
        }
        return nameParts[0];
    }

    private List<Inferrer.Entry> legalizeNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(this::legalizeName).toList();
    }

    private Inferrer.Entry legalizeName(Inferrer.Entry entry) {
        String legalName = entry.name()
            .replaceAll("[^a-zA-Z0-9_-]", "")
            .replaceAll("^[_-]+", "")
            .replaceAll("[_-]+$", "")
            .replaceAll("([_-])[_-]+", "$1");
        return entry.changeName(StringUtils.firstNonEmpty(legalName, defaultName(entry)));
    }

    private String defaultName(Inferrer.Entry entry) {
        return entry.type().getTerm();
    }

    private List<Inferrer.Entry> ensureUniqueNames(List<Inferrer.Entry> entries) {
        Set<String> names = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        entries.forEach(entry -> {
            String name = entry.name();
            if (names.contains(name)) {
                duplicates.add(name);
            }
            names.add(name);
        });

        AtomicLong counter = new AtomicLong(1);
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
