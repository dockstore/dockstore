package io.dockstore.webservice.core;

import io.dockstore.webservice.CustomWebApplicationException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Base class for Converters that convert between List and delimited String representation.
 */
@Converter(autoApply = true)
public abstract class DelimitedValuesConverter implements AttributeConverter<List<String>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(DelimitedValuesConverter.class);

    private final String delimiter;

    protected DelimitedValuesConverter(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        // Determine the list of values that contain the delimiter.
        final List<String> offenders = list.stream()
                .filter(value -> value.contains(delimiter)).toList();

        // If one or more values contain the delimiter, log and throw.
        if (!offenders.isEmpty()) {
            final boolean isMultiple = offenders.size() > 1;
            final String commaSeparated = offenders.stream()
                    .map(value -> String.format("'%s'", value))
                    .collect(Collectors.joining(", "));
            final String errorMessage = String.format("%s %s contain%s the delimiter '%s'",
                    getSubject(isMultiple), commaSeparated, isMultiple ? "" : "s", delimiter);
            LOG.error(errorMessage);
            throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }

        // Return the delimiter-separated string.
        return String.join(delimiter, list);
    }

    @Override
    public List<String> convertToEntityAttribute(String string) {
        if (string == null) { // The database column may intentionally store an empty string
            return new ArrayList<>();
        }
        return Arrays.asList(string.split(delimiter));
    }

    public String getSubject(boolean isPlural) {
        return "Value" + (isPlural ? "s" : "");
    }
}
