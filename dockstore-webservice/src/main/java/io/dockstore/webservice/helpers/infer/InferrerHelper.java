package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.webservice.helpers.FileTree;
import io.dockstore.webservice.CustomWebApplicationException;
import java.io.StringWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.ObjectUtils;
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
        return List.of(
            // CWL
            new BasicInferrer(DescriptorLanguage.CWL) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".cwl");
                }
                protected EntryType calculateType(FileTree fileTree, String path) {
                    String content = fileTree.readFile(path);
                    if (lineContainsRegex("^class:\\s*Workflow", content)) {
                        return EntryType.WORKFLOW;
                    }
                    if (lineContainsRegex("^class:\\s*CommandLineTool", content)) {
                        return EntryType.APPTOOL;
                    }
                    return null;
                }
            },
            // WDL
            new BasicInferrer(DescriptorLanguage.WDL) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".wdl");
                }
                protected EntryType calculateType(FileTree fileTree, String path) {
                    String content = fileTree.readFile(path);
                    if (lineContainsRegex("^workflow\\s", content)) {
                        return EntryType.WORKFLOW;
                    }
                    return null;
                }
                protected String calculateName(FileTree fileTree, String path) {
                    String content = fileTree.readFile(path);
                    return groupFromLineContainingRegex("^workflow\\s+(\\S+)\\s", 1, content);
                }
            },
            // NEXTFLOW
            new BasicInferrer(DescriptorLanguage.NEXTFLOW) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith("/nextflow.config");
                }
                protected List<String> calculateReferencedPaths(FileTree fileTree, String path) {
                    return List.of();
                }
            },
            // GALAXY
            new BasicInferrer(DescriptorLanguage.GXFORMAT2) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ga");
                }
            },
            // JUPYTER
            new BasicInferrer(DescriptorLanguage.JUPYTER) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ipynb");
                }
                protected List<String> calculateReferencedPaths(FileTree fileTree, String path) {
                    return List.of();
                }
                protected DescriptorLanguageSubclass calculateSubclass(FileTree fileTree, String path) {
                    String content = fileTree.readFile(path).toLowerCase();
                    for (DescriptorLanguageSubclass subclass: DescriptorLanguageSubclass.values()) {
                        String quotedSubclass = '"' + subclass.getShortName() + '"';
                        if (content.contains(quotedSubclass)) {
                            return subclass;
                        }
                    }
                    return DescriptorLanguageSubclass.PYTHON;
                }
            }

        );
    }

    public List<Inferrer.Entry> refine(List<Inferrer.Entry> entries) {
        return ensureUniqueNames(legalizeNames(setMissingNames(removeDuplicatePaths(entries))));
    }

    private List<Inferrer.Entry> removeDuplicatePaths(List<Inferrer.Entry> entries) {
        Set<String> paths = new HashSet<>();
        return entries.stream().filter(entry -> paths.add(entry.path())).toList();
    }

    private List<Inferrer.Entry> setMissingNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(entry -> entry.changeName(ObjectUtils.firstNonNull(entry.name(), calculateNameFromPath(entry.path()), entry.type().getTerm()))).toList();
    }

    private String calculateNameFromPath(String path) {
        try {
            return Paths.get(path).getFileName().toString().split("\\.")[0];
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private List<Inferrer.Entry> legalizeNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(entry -> entry.changeName(legalizeName(entry.name()))).toList();
    }

    private String legalizeName(String name) {
        return name
            .replaceAll("[^a-zA-Z0-9_-]", "")
            .replaceAll("^[_-]+", "")
            .replaceAll("[_-]+$", "")
            .replaceAll("([_-])[_-]+", "\\1");
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
                } while (!taken.contains(newName));
                name = newName;
            }
            taken.add(name);
            return entry.changeName(name);
        }).toList();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public String toDockstoreYml(List<Inferrer.Entry> entries) {
        // "Refine" the entries, to fix things like missing, colliding, or illegal names.
        entries = refine(entries);
        // Construct map that contains an abstract representation of the .dockstore.yml.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", "1.2");
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
        List<Map<String, Object>> fieldValue = entries.stream().filter(entry -> entry.type() == type).map(this::toMap).toList();
        if (!fieldValue.isEmpty()) {
            map.put(fieldName, fieldValue);
        }
    }

    private Map<String, Object> toMap(Inferrer.Entry entry) { 
        Map<String, Object> map = new LinkedHashMap<>();
        if (entry.name() != null) {
            map.put("name", entry.name());
        }
        if (entry.type() == EntryType.NOTEBOOK) {
            map.put("format", entry.language().toString());
            map.put("language", entry.subclass().toString());
            map.put("path", entry.path());
        } else {
            map.put("subclass", entry.language().toString());
            map.put("primaryDescriptorPath", entry.path());
        }
        return map;
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
