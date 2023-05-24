package io.dockstore.webservice.core.tooltester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.DescriptorTypeVersionConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptorTypeConverterTest {

    @Test
    void testConvertToDatabaseColumn() {
        final DescriptorTypeVersionConverter descriptorTypeConverter = new DescriptorTypeVersionConverter();
        List<String> descriptorTypeVersions = List.of("1.0", "1.1");
        assertEquals("1.0\t1.1", descriptorTypeConverter.convertToDatabaseColumn(descriptorTypeVersions));

        assertThrows(CustomWebApplicationException.class,
            () -> descriptorTypeConverter.convertToDatabaseColumn(List.of("\t1.0")), "Version string containing delimiter should throw exception");
    }

    @Test
    void testConvertToEntityAttribute() {
        final DescriptorTypeVersionConverter descriptorTypeConverter = new DescriptorTypeVersionConverter();

        List<String> descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute(null);
        assertTrue(descriptorTypeVersions.isEmpty());

        descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute("1.0");
        assertEquals(1, descriptorTypeVersions.size(), "Should have one version");
        assertTrue(descriptorTypeVersions.contains("1.0"));

        descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute("1.0\t1.1");
        assertEquals(2, descriptorTypeVersions.size(), "Should have two versions");
        assertTrue(descriptorTypeVersions.contains("1.0"));
        assertTrue(descriptorTypeVersions.contains("1.1"));
    }
}
