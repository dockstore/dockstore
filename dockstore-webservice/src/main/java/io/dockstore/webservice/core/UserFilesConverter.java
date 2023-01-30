package io.dockstore.webservice.core;

import javax.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class UserFilesConverter extends DelimitedValuesConverter {
    private static final Logger LOG = LoggerFactory.getLogger(UserFilesConverter.class);

    protected Logger getLogger() {
        return LOG;
    }

    protected String getDelimiter() {
        return "/t";
    }

    protected String getSubject(boolean isPlural) {
        return "User file" + (isPlural ? "s" : "");
    }
}
