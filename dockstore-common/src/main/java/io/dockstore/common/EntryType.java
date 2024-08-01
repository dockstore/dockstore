package io.dockstore.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum for available entry types on Dockstore
 * @author aduncan
 * @since 1.8.0
 */
@Schema(enumAsRef = true)
public enum EntryType {
    TOOL("tool"),
    WORKFLOW("workflow"),
    SERVICE("service"),
    APPTOOL("tool"),
    NOTEBOOK("notebook");

    private final String term;
    private Set<DescriptorLanguageSubclass> subclasses;

    EntryType(String term) {
        this.term = term;
        subclasses = Arrays.stream(DescriptorLanguageSubclass.values()).filter(subclass -> subclass.getEntryTypes().contains(this)).collect(Collectors.toSet());
    }

    public String getTerm() {
        return term;
    }

    public Set<DescriptorLanguageSubclass> getSubclasses() {
        return subclasses;
    }
}
