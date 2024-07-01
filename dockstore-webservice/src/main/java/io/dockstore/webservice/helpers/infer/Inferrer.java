package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.util.List;

public interface Inferrer {

    List<Entry> infer(FileTree fileTree);

    public record Entry(EntryType type, DescriptorLanguage language, DescriptorLanguageSubclass subclass, String path, String name) {
        public Entry changeName(String newName) {
            return new Entry(type, language, subclass, path, newName);
        }
    }
}
