package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InferredEntriesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InferredEntriesHelper.class);

    public List<InferredEntry> infer(FileTree fileTree) {
        List<String> allPaths = fileTree.listAllFilePaths();
        allPaths.forEach(p -> LOG.error("PATH " + p));
        List<InferredEntry> entries = new ArrayList<>();
        for (DescriptorLanguage language: getLanguages()) {
            Predicate<String> isPrimary = getPrimaryPredicate(language);
            List<String> paths = allPaths.stream().filter(isPrimary).toList();
            paths.forEach(p -> LOG.error("PRIMARY PATH " + p));
            paths = removeNonPrimaryDescriptors(fileTree, paths);
            for (String path: paths) {
                EntryType type = calculateType(fileTree, path, language);
                String name = calculateName(fileTree, path, language);
                entries.add(new InferredEntry(type, language, path, name));
            }
        }
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
            return s -> s.endsWith(".ga");
        case JUPYTER:
            return s -> s.endsWith(".ipynb");
        default:
            throw new UnsupportedOperationException();
        }
    }

    private List<String> removeNonPrimaryDescriptors(FileTree fileTree, List<String> paths) {
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
            throw new UnsupportedOperationException();
        }
    }

    private EntryType calculateTypeCWL(FileTree fileTree, String path) {
        // TODO differentiate between workflow and tool
        // "^class:.*CommandLineTool$"
        return EntryType.WORKFLOW;
    }

    private EntryType calculateTypeWDL(FileTree fileTree, String path) {
        // TODO differentiate between workflow and tool
        return EntryType.WORKFLOW;
    }

    private String calculateName(FileTree fileTree, String path, DescriptorLanguage language) {
        // TODO extract name from entry within file itself, if it exists
        // Use more robust name-from-file extraction technique
        String[] parts = path.split("\\/");
        return parts[parts.length - 1].split("\\.")[0];
    }

    private List<InferredEntry> postprocessNames(List<InferredEntry> entries) {
        // TODO
        // if there's only a single entry, remove the name
        // remove everything but alpha characters
        // make sure there are no duplicates
        return entries;
    }

    public String toDockstoreYml(List<InferredEntry> entries) {
        // TODO
        // construct yaml
        // parse to make sure we didn't generate an invalid .dockstore.yml
        throw new UnsupportedOperationException();
    }

    public record InferredEntry(EntryType type, DescriptorLanguage language, String path, String name) {
        public InferredEntry setName(String newName) {
            return new InferredEntry(type, language, path, newName);
        }
    }
}
