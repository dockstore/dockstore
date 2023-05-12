package io.dockstore.webservice.core;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class DescriptorTypeConverter implements AttributeConverter<List<String>, String> {
    /**
     * Checksums are stored in the database as a string with the format type:checksum and are comma separated.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DescriptorTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(List<String> descriptorTypes) {
        String dt = "";
        if (descriptorTypes != null && !descriptorTypes.isEmpty()) {
            dt = String.join(",", descriptorTypes);
        }
        return dt;
    }

    @Override
    public List<String> convertToEntityAttribute(String descriptorTypes) {
        List dt = new ArrayList();
        if (StringUtils.isNotEmpty(descriptorTypes)) {
            dt = Arrays.asList(descriptorTypes.split(","));
        }
        return dt;
    }
}
