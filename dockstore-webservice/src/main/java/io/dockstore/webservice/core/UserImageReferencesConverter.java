package io.dockstore.webservice.core;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class UserImageReferencesConverter extends DelimitedValuesConverter {

    public UserImageReferencesConverter() {
        super("\t");
    }

    @Override
    public String getSubject(boolean isPlural) {
        return "User image reference" + (isPlural ? "s" : "");
    }
}
