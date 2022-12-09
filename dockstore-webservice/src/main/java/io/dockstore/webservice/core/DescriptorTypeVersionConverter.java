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
public class DescriptorTypeVersionConverter implements AttributeConverter<List<String>, String> {
    /**
     * Descriptor type versions are stored in the database as a string and are comma separated.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DescriptorTypeVersionConverter.class);
    public static final String DELIMITER = "\t"; // Use a non-comma delimiter because it's less likely to appear in a version string

    @Override
    public String convertToDatabaseColumn(List<String> descriptorTypeVersions) {
        final List<String> versionsContainingDelimiter = descriptorTypeVersions.stream()
                .filter(version -> version.contains(DELIMITER))
                .collect(Collectors.toList());

        // Check that the version string does not contain the delimiter
        if (!versionsContainingDelimiter.isEmpty()) {
            final boolean isMultipleVersions = versionsContainingDelimiter.size() > 1;
            final String versionNames = versionsContainingDelimiter.stream()
                    .map(versionName -> String.format("'%s'", versionName))
                    .collect(Collectors.joining(", "));
            final String errorMessage = String.format("Descriptor type version%s %s contain%s the delimiter %s",
                    isMultipleVersions ? "s" : "", versionNames, isMultipleVersions ? "" : "s", DELIMITER);
            LOG.error(errorMessage);
            throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }

        if (descriptorTypeVersions != null && !descriptorTypeVersions.isEmpty()) {
            return String.join(DELIMITER, descriptorTypeVersions);
        }
        return null;
    }

    @Override
    public List<String> convertToEntityAttribute(String descriptorTypeVersionString) {
        List<String> descriptorTypeVersions = new ArrayList<>();
        if (StringUtils.isNotEmpty(descriptorTypeVersionString)) {
            descriptorTypeVersions = Arrays.asList(descriptorTypeVersionString.split(DELIMITER));
        }
        return descriptorTypeVersions;
    }
}
