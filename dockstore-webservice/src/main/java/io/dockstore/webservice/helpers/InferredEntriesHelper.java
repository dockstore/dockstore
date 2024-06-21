package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.Inferrer.InferredEntry;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class InferredEntriesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InferredEntriesHelper.class);

    public List<InferredEntry> infer(FileTree fileTree) {
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
            },
            // GALAXY
            new BasicInferrer(DescriptorLanguage.GXFORMAT2) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ga") || path.endsWith(".ga2");
                }
            },
            // JUPYTER
            new BasicInferrer(DescriptorLanguage.JUPYTER) {
                protected boolean isDescriptorPath(String path) {
                    return path.endsWith(".ipynb");
                }
            }

        );
    }

    private List<InferredEntry> postprocessEntries(List<InferredEntry> entries) {
        // TODO subsitute invalid names with something legal
        // TODO change any duplicate names so they are unique
        // TODO if any paths are the same, pick an entry
        return entries;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public String toDockstoreYml(List<InferredEntry> entries) {
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

    private void putEntries(Map<String, Object> map, String fieldName, List<InferredEntry> entries, EntryType type) {
        List<Map<String, Object>> subset = entries.stream().filter(entry -> entry.type() == type).map(entry -> {
            Map<String, Object> submap = new LinkedHashMap<>();
            if (entry.name() != null) {
                submap.put("name", entry.name());
            }
            if (entry.type() == EntryType.NOTEBOOK) {
                submap.put("format", entry.language().toString());
                // TODO add notebook language
                submap.put("path", entry.path());
            } else {
                submap.put("subclass", entry.language().toString());
                submap.put("primaryDescriptorPath", entry.path());
            }
            return submap;
        }).toList();

        if (!subset.isEmpty()) {
            map.put(fieldName, subset);
        }
    }
}
