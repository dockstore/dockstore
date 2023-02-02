package io.dockstore.webservice.core;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class UserFilesConverter extends DelimitedValuesConverter {

    public UserFilesConverter() {
        super("\t");
    }

    @Override
    public String getSubject(boolean isPlural) {
        return "User file" + (isPlural ? "s" : "");
    }
}
