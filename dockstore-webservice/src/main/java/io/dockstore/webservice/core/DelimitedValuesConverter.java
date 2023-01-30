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

/** 
 * Base class for Converters that convert between List and delimited String representation.
 */
@Converter(autoApply = true)
public abstract class DelimitedValuesConverter implements AttributeConverter<List<String>, String> {

    protected abstract Logger getLogger();
    protected abstract String getDelimiter();
    protected abstract String getSubject(boolean isPlural);

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        final String delimiter = getDelimiter();

        // Determine the list of values that contain the delimiter.
        final List<String> containingDelimiter = list.stream()
                .filter(value -> value.contains(delimiter))
                .collect(Collectors.toList());

        // If one or more values contain the delimiter, log and throw.
        if (!containingDelimiter.isEmpty()) {
            final boolean isMultiple = containingDelimiter.size() > 1;
            final String commaSeparated = containingDelimiter.stream()
                    .map(value -> String.format("'%s'", value))
                    .collect(Collectors.joining(", "));
            final String errorMessage = String.format("%s %s contain%s the delimiter %s",
                    getSubject(isMultiple), commaSeparated, isMultiple ? "" : "s", delimiter);
            getLogger().error(errorMessage);
            throw new CustomWebApplicationException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }

        // If the list contains one or more values, return a delimiter-separated string, otherwise null.
        if (list != null && !list.isEmpty()) {
            return String.join(delimiter, list);
        }
        return null;
    }

    @Override
    public List<String> convertToEntityAttribute(String string) {
        if (StringUtils.isNotEmpty(string)) {
            return Arrays.asList(string.split(getDelimiter()));
        } else {
            return new ArrayList<>();
        }
    }
}
