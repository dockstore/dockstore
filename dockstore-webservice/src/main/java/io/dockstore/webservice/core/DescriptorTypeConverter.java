package io.dockstore.webservice.core;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class DescriptorTypeConverter extends DelimitedValuesConverter {
    /**
     * Descriptor types are stored in the database as a string and are comma separated.
     */

    public DescriptorTypeConverter() {
        super(",");
    }

    @Override
    public String getSubject(boolean isPlural) {
        return "Descriptor type" + (isPlural ? "s" : "");
    }
}
