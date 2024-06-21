package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import java.util.List;

public interface Inferrer {

    List<InferredEntry> infer(FileTree fileTree);

    public record InferredEntry(EntryType type, DescriptorLanguage language, String path, String name) {
        public InferredEntry changeName(String newName) {
            return new InferredEntry(type, language, path, newName);
        }
    }
}
