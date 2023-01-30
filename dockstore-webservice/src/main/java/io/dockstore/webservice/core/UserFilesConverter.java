package io.dockstore.webservice.core;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class UserFilesConverter implements AttributeConverter<List<String>, String> {
    /**
     * Descriptor type file are stored in the database as a string and are separated by DELIMITER.
     */
    private static final Logger LOG = LoggerFactory.getLogger(UserFilesConverter.class);
    public static final String DELIMITER = "\t"; // Use a non-comma delimiter because it's less likely to appear in a file string

    @Override
    public String convertToDatabaseColumn(List<String> userFiles) {
        final List<String> containingDelimiter = userFiles.stream()
                .filter(file -> file.contains(DELIMITER))
                .collect(Collectors.toList());

        // Check that the file string does not contain the delimiter
        if (!containingDelimiter.isEmpty()) {
            final boolean isMultiple = containingDelimiter.size() > 1;
            final String commaSeparated = containingDelimiter.stream()
                    .map(file -> String.format("'%s'", file))
                    .collect(Collectors.joining(", "));
            final String errorMessage = String.format("User file%s %s contain%s the delimiter %s",
                    isMultiple ? "s" : "", commaSeparated, isMultiple ? "" : "s", DELIMITER);
            LOG.error(errorMessage);
            throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }

        if (userFiles != null && !userFiles.isEmpty()) {
            return String.join(DELIMITER, userFiles);
        }
        return null;
    }

    @Override
    public List<String> convertToEntityAttribute(String userFilesString) {
        List<String> userFiles = new ArrayList<>();
        if (StringUtils.isNotEmpty(userFilesString)) {
            userFiles = Arrays.asList(userFilesString.split(DELIMITER));
        }
        return userFiles;
    }
}
