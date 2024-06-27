package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BasicInferrer implements Inferrer {

    private static final Logger LOG = LoggerFactory.getLogger(BasicInferrer.class);
    private static final Pattern POSSIBLE_PATH = Pattern.compile("[a-zA-Z0-9./_-]+\\.[a-zA-Z0-9]+");
    private final DescriptorLanguage language;

    public BasicInferrer(DescriptorLanguage language) {
        this.language = language;
    }

    @Override
    public List<Entry> infer(FileTree fileTree) {
        // Get a list of paths that are probably descriptors.
        List<String> paths = fileTree.listPaths().stream().filter(this::isDescriptorPath).toList();
        // Remove paths that probably correspond to tests, subtasks, etc.
        paths = removeTestPaths(paths);
        // Remove paths that are referenced from other descriptors.
        paths = removeReferencedPaths(fileTree, paths);
        // For each path that remains, calculate its entry type.
        // If we're successful, add it as an inferred entry.
        List<Entry> entries = new ArrayList<>();
        for (String path: paths) {
            EntryType type = calculateType(fileTree, path);
            String name = calculateName(fileTree, path);
            if (type != null) {
                entries.add(new Entry(type, language, path, name));
            }
        }
        // Remove entries that don't appear to reference other descriptors.
        // return removeStandaloneEntries(fileTree, entries);
        return entries;
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
        paths.forEach(path -> unreferencedPaths.removeAll(calculateReferencedPaths(fileTree, path)));
        return toList(unreferencedPaths);
    }

    protected List<String> calculateReferencedPaths(FileTree fileTree, String path) {
        Set<String> referencedPaths = new LinkedHashSet<>();
        String content = removeComments(fileTree.readFile(path));
        Matcher matcher = POSSIBLE_PATH.matcher(content);
        while (matcher.find()) {
            String foundPath = matcher.group();
            if (isDescriptorPath(path)) {
                String referencedPath = Paths.get(path).resolve(foundPath).normalize().toString();
                referencedPaths.add(referencedPath);
            }
        }
        return toList(referencedPaths);
    }

    protected EntryType calculateType(FileTree fileTree, String path) {
        Set<EntryType> entryTypes = language.getEntryTypes();
        if (entryTypes.size() == 1) {
            return entryTypes.iterator().next();
        }
        throw new UnsupportedOperationException();
    }

    protected String calculateName(FileTree fileTree, String path) {
        // TODO extract name from entry within file itself, if it exists
        // Use more robust name-from-file extraction technique
        String[] parts = path.split("\\/");
        return parts[parts.length - 1].split("\\.")[0];
    }

    protected String removeComments(String content) {
        return content.replaceAll("#.*", "");
    }

    /*
    protected List<Entry> removeStandaloneEntries(FileTree fileTree, List<Entry> entries) {
        Map<Entry, Integer> entryToReferencedCount = entries.stream().collect(Collectors.toMap(entry -> entry, entry -> calculateReferencedPaths(fileTree, entry.path()).size()));
        int maxReferencedCount = entryToReferencedCount.values().stream().max(Integer::compare).orElse(0);
        if (maxReferencedCount > 0) {
            return entries.stream().filter(entry -> entryToReferencedCount.get(entry) > 0).toList();
        } else {
            return entries;
        }
    }
    */

    protected static boolean lineContainsRegex(String regex, String s) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(s).find();
    }

    protected static <T> List<T> toList(Collection<? extends T> values) {
        return new ArrayList<T>(values);
    }
}
