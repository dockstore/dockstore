package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class InferredEntriesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InferredEntriesHelper.class);

    public List<InferredEntry> infer(FileTree fileTree) {

        // Get a list of all paths in the repo.
        List<String> allPaths = fileTree.listPaths();
        allPaths.forEach(p -> LOG.error("PATH " + p));

        List<InferredEntry> entries = new ArrayList<>();
        // For each descriptor language, examine the paths and try to determine
        // which paths point at the primary descriptor of an entry.
        for (DescriptorLanguage language: getLanguages()) {

            // Determine the list of paths that could be a primary descriptor.
            Predicate<String> isPrimary = getPrimaryPredicate(language);
            List<String> paths = allPaths.stream().filter(isPrimary).toList();
            paths.forEach(p -> LOG.error("POSSIBLE PRIMARY PATH " + p));

            // Remove paths that probably correspond to tests, subtasks, etc.
            paths = removeTestPaths(paths);
            paths.forEach(p -> LOG.error("AFTER removeTestPaths PATH " + p));

            // Remove paths that are referenced from other descriptors.
            paths = removeReferencedPaths(fileTree, paths);
            paths.forEach(p -> LOG.error("PRIMARY PATH " + p));

            // For each path that remains, calculate its entry type.
            // If we're successful, add it as an inferred entry.
            for (String path: paths) {
                EntryType type = calculateType(fileTree, path, language);
                String name = calculateName(fileTree, path, language);
                LOG.error("NAME AND TYPE " + name + " " + type + " " + path + " " + language);
                if (type != null) {
                    entries.add(new InferredEntry(type, language, path, name));
                }
            }
        }

        // Postprocess the list of inferred entries to do things like make sure
        // the entry names are legal.
        return postprocessNames(entries);
    }

    private List<DescriptorLanguage> getLanguages() {
        return List.of(
            DescriptorLanguage.CWL,
            DescriptorLanguage.WDL,
            DescriptorLanguage.NEXTFLOW,
            DescriptorLanguage.GXFORMAT2,
            DescriptorLanguage.JUPYTER
        );
    }

    private Predicate<String> getPrimaryPredicate(DescriptorLanguage language) {
        switch (language) {
        case CWL:
            return s -> s.endsWith(".cwl");
        case WDL:
            return s -> s.endsWith(".wdl");
        case NEXTFLOW:
            return s -> s.endsWith("/nextflow.config");
        case GXFORMAT2:
            return s -> s.endsWith(".ga") || s.endsWith(".ga2");
        case JUPYTER:
            return s -> s.endsWith(".ipynb");
        default:
            throw new UnsupportedOperationException();
        }
    }

    private List<String> removeTestPaths(List<String> paths) {
        return paths.stream().filter(path -> {
            String lower = path.toLowerCase();
            return !(lower.contains("test") || lower.contains("archive") || lower.contains("debug"));
        }).toList();
    }

    private List<String> removeReferencedPaths(FileTree fileTree, List<String> paths) {
        Pattern possiblePath = Pattern.compile("[a-zA-Z0-9_/-]+\\.[a-zA-Z0-9]+");
        Set<String> unreferenced = new LinkedHashSet<>(paths);
        for (String path: paths) {
            Path fromDirectory = Paths.get(path).getParent();
            String content = fileTree.readFile(path);
            Matcher matcher = possiblePath.matcher(removeComments(content));
            LOG.error("UNCOMMENTED " + removeComments(content));
            while (matcher.find()) {
                String found = matcher.group();
                String toPath = fromDirectory.resolve(found).normalize().toString();
                LOG.error("POSSIBLE PATH {} {} {}", fromDirectory, found, toPath);
                unreferenced.remove(toPath);
            }
        }
        return new ArrayList<>(unreferenced);
    }

    private String removeComments(String content) {
        return content.replaceAll("#.*", "");
    }

    private EntryType calculateType(FileTree fileTree, String path, DescriptorLanguage language) {
        Set<EntryType> entryTypes = language.getEntryTypes();
        if (entryTypes.size() == 1) {
            return entryTypes.iterator().next();
        }
        switch (language) {
        case CWL:
            return calculateTypeCWL(fileTree, path);
        case WDL:
            return calculateTypeWDL(fileTree, path);
        default:
            return null;
        }
    }

    private EntryType calculateTypeCWL(FileTree fileTree, String path) {
        String content = fileTree.readFile(path);
        if (lineContainsRegex("^class:\\s*Workflow", content)) {
            return EntryType.WORKFLOW;
        }
        if (lineContainsRegex("^class:\\s*CommandLineTool", content)) {
            return EntryType.APPTOOL;
        }
        return null;
    }

    private EntryType calculateTypeWDL(FileTree fileTree, String path) {
        // TODO differentiate between workflow and tool
        String content = fileTree.readFile(path);
        if (lineContainsRegex("^workflow\\s", content)) {
            return EntryType.WORKFLOW;
        }
        return null;
    }

    private boolean lineContainsRegex(String regex, String s) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(s).find();
    }

    private String calculateName(FileTree fileTree, String path, DescriptorLanguage language) {
        // TODO extract name from entry within file itself, if it exists
        // Use more robust name-from-file extraction technique
        String[] parts = path.split("\\/");
        return parts[parts.length - 1].split("\\.")[0];
    }

    private List<InferredEntry> postprocessNames(List<InferredEntry> entries) {
        // TODO subsitute invalid names with something legal
        // TODO change any duplicate names so they are unique
        return entries;
    }

    public String toDockstoreYml(List<InferredEntry> entries) {
        // construct map
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", "1.2");
        putEntriesField(map, "tools", entries, EntryType.TOOL);
        putEntriesField(map, "workflows", entries, EntryType.WORKFLOW);
        putEntriesField(map, "notebooks", entries, EntryType.NOTEBOOK);
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

    private void putEntriesField(Map<String, Object> map, String fieldName, List<InferredEntry> entries, EntryType type) {
        List<Map<String, Object>> subset = entries.stream().filter(entry -> entry.type() == type).map(entry -> {
            Map<String, Object> submap = new LinkedHashMap<>();
            if (entry.name() != null) {
                submap.put("name", entry.name());
            }
            if (entry.type() == EntryType.NOTEBOOK) {
                submap.put("format", entry.language().toString().toLowerCase());
                submap.put("path", entry.path());
            } else {
                submap.put("subclass", entry.language().toString().toLowerCase());
                submap.put("primaryDescriptorPath", entry.path());
            }
            return submap;
        }).toList();

        if (!subset.isEmpty()) {
            map.put(fieldName, subset);
        }
    }

    public record InferredEntry(EntryType type, DescriptorLanguage language, String path, String name) {
        public InferredEntry changeName(String newName) {
            return new InferredEntry(type, language, path, newName);
        }
    }
}
