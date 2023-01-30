package io.dockstore.webservice.core;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class UserFilesConverter extends DelimitedValuesConverter {

    public UserFilesConverter() {
        super("/t", true);
    }

    public String getSubject(boolean isPlural) {
        return isPlural ? "User files" : "User file";
    }
}
