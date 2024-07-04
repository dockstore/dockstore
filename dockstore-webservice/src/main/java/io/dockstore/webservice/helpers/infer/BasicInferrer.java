package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        // Remove paths that likely correspond to tests, subtasks, etc.
        paths = removeTestPaths(paths);
        // Remove paths that are referenced from other descriptors.
        paths = removeReferencedPaths(fileTree, paths);
        // For each path that remains, attempt to infer the entries.
        return paths.stream().flatMap(path -> infer(fileTree, path).stream()).toList();
    }

    public List<Entry> infer(FileTree fileTree, String path) {
        if (isDescriptorPath(path)) {
            EntryType type = calculateType(fileTree, path);
            if (type != null) {
                String name = calculateName(fileTree, path);
                DescriptorLanguageSubclass subclass = calculateSubclass(fileTree, path, type);
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
                try {
                    String referencedPath = Paths.get(path).resolve(foundPath).normalize().toString();
                    referencedPaths.add(referencedPath);
                } catch (InvalidPathException e) {
                    // If either path was invalid, ignore and continue.
                }
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
        return null;
    }

    protected DescriptorLanguageSubclass calculateSubclass(FileTree fileTree, String path, EntryType type) {
        Set<DescriptorLanguageSubclass> subclasses = DescriptorLanguageSubclass.valuesForEntryType(type);
        if (subclasses.size() == 1) {
            return subclasses.iterator().next();
        }
        throw new UnsupportedOperationException();
    }

    protected String removeComments(String content) {
        return content.replaceAll("#.*", "");
    }

    protected static boolean lineContainsRegex(String regex, String s) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(s).find();
    }

    protected static String groupFromLineContainingRegex(String regex, int groupIndex, String s) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(s);
        if (matcher.find()) {
            return matcher.group(groupIndex);
        } else {
            return null;
        }
    }

    protected static <T> List<T> toList(Collection<? extends T> values) {
        return new ArrayList<T>(values);
    }
}
