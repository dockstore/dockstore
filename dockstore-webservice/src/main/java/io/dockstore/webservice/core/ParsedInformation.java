package io.dockstore.webservice.core;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import io.dockstore.common.DescriptorLanguage;

/**
 * Putting DescriptorLanguage here instead of using a map with DescriptorLanguage because there may be cases where the language is
 * irrelevant. For example, workflows only have one language.
 */
@Entity
public class ParsedInformation {
    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private DescriptorLanguage descriptorLanguage;
    private boolean hasHTTPImports = false;
    private boolean hasLocalImports = false;

    public DescriptorLanguage getDescriptorLanguage() {
        return descriptorLanguage;
    }

    public void setDescriptorLanguage(DescriptorLanguage descriptorLanguage) {
        this.descriptorLanguage = descriptorLanguage;
    }

    public boolean isHasHTTPImports() {
        return hasHTTPImports;
    }

    public void setHasHTTPImports(boolean hasHTTPImports) {
        this.hasHTTPImports = hasHTTPImports;
    }

    public boolean isHasLocalImports() {
        return hasLocalImports;
    }

    public void setHasLocalImports(boolean hasLocalImports) {
        this.hasLocalImports = hasLocalImports;
    }
}
