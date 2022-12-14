package io.dockstore.webservice.core.tooltester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.DescriptorTypeVersionConverter;
import java.util.List;
import org.junit.Test;

public class DescriptorTypeConverterTest {

    @Test
    public void testConvertToDatabaseColumn() {
        final DescriptorTypeVersionConverter descriptorTypeConverter = new DescriptorTypeVersionConverter();
        List<String> descriptorTypeVersions = List.of("1.0", "1.1");
        assertEquals("1.0\t1.1", descriptorTypeConverter.convertToDatabaseColumn(descriptorTypeVersions));

        assertThrows("Version string containing delimiter should throw exception", CustomWebApplicationException.class,
                () -> descriptorTypeConverter.convertToDatabaseColumn(List.of("\t1.0")));
    }

    @Test
    public void testConvertToEntityAttribute() {
        final DescriptorTypeVersionConverter descriptorTypeConverter = new DescriptorTypeVersionConverter();

        List<String> descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute(null);
        assertTrue(descriptorTypeVersions.isEmpty());

        descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute("1.0");
        assertEquals("Should have one version", 1, descriptorTypeVersions.size());
        assertTrue(descriptorTypeVersions.contains("1.0"));

        descriptorTypeVersions = descriptorTypeConverter.convertToEntityAttribute("1.0\t1.1");
        assertEquals("Should have two versions", 2, descriptorTypeVersions.size());
        assertTrue(descriptorTypeVersions.contains("1.0"));
        assertTrue(descriptorTypeVersions.contains("1.1"));
    }
}
