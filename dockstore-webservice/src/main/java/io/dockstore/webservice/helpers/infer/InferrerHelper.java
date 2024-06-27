package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
                // TODO get name out of WDL file
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
            }

        );
    }

    private List<Inferrer.Entry> postprocessEntries(List<Inferrer.Entry> entries) {
        return deduplicateNames(legalizeNames(deduplicatePaths(entries)));
    }

    private List<Inferrer.Entry> deduplicatePaths(List<Inferrer.Entry> entries) {
        return entries.stream().collect(Collections.toMap(Entry::path, entry -> entry)).values();
    }

    private List<Inferrer.Entry> legalizeNames(List<Inferrer.Entry> entries) {
        return entries.stream().map(entry -> entry.changeName(legalizeName(entry.name()))).toList();
    }

    private String legalizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "").replaceAll("([_-])[_-]+", "\\1");
    }

    private List<Inferrer.Entry> deduplicateNames(List<Inferrer.Entry> entries) {
        AtomicLong counter = new AtomicLong();
        Set<String> takenNames = new HashSet<>();
        return entries.stream().map(entry -> {
            String name = entry.name();
            String candidateName = name;
            while (takenNames.contains(candidateName)) {
                candidateName = name + counter.get();
                counter.increment();
            }
            takenNames.add(candidateName);
            return entry.changeName(candidateName);
        });
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public String toDockstoreYml(List<Inferrer.Entry> entries) {
        entries = postprocessEntries(entries);
        // construct map
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", "1.2");
        putEntries(map, "tools", entries, EntryType.APPTOOL);
        putEntries(map, "workflows", entries, EntryType.WORKFLOW);
        putEntries(map, "notebooks", entries, EntryType.NOTEBOOK);
        // convert to string representation
        StringWriter writer = new StringWriter();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setIndent(4);
        dumperOptions.setIndicatorIndent(2);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        new Yaml(dumperOptions).dump(map, writer);
        // TODO parse to make sure we didn't generate an invalid .dockstore.yml
        return writer.toString();
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
            // TODO add notebook language
            map.put("path", entry.path());
        } else {
            map.put("subclass", entry.language().toString());
            map.put("primaryDescriptorPath", entry.path());
        }
        return map;
    }
}
