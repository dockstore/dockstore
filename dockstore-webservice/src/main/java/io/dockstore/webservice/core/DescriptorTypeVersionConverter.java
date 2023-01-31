package io.dockstore.webservice.core;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class DescriptorTypeVersionConverter extends DelimitedValuesConverter {

    public DescriptorTypeVersionConverter() {
        super("\t");
    }

    @Override
    public String getSubject(boolean isPlural) {
        return "Descriptor type version" + (isPlural ? "s" : "");
    }
}
