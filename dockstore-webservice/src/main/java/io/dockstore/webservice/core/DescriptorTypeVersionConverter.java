package io.dockstore.webservice.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class DescriptorTypeVersionConverter implements AttributeConverter<List<String>, String> {
    /**
     * Descriptor type versions are stored in the database as a string and are comma separated.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DescriptorTypeVersionConverter.class);

    @Override
    public String convertToDatabaseColumn(List<String> descriptorTypeVersions) {
        if (descriptorTypeVersions != null && !descriptorTypeVersions.isEmpty()) {
            return String.join(",", descriptorTypeVersions);
        }
        return null;
    }

    @Override
    public List<String> convertToEntityAttribute(String descriptorTypeVersionString) {
        List<String> descriptorTypeVersions = new ArrayList<>();
        if (StringUtils.isNotEmpty(descriptorTypeVersionString)) {
            descriptorTypeVersions = Arrays.asList(descriptorTypeVersionString.split(","));
        }
        return descriptorTypeVersions;
    }
}
