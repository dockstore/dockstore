package io.dockstore.webservice.core;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import io.dockstore.common.DescriptorLanguage;

/**
 * Putting DescriptorLanguage here instead of using a map with DescriptorLanguage because there may be cases where the language is
 * irrelevant. For example, workflows only have one language.
 */
@Embeddable
public class ParsedInformation {
    @Enumerated(EnumType.STRING)
    DescriptorLanguage descriptorLanguage;
    private boolean hasHTTPImports;
    private boolean hasLocalImports;
}
