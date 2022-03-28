package io.dockstore.webservice.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class ChecksumConverter implements AttributeConverter<List<Checksum>, String> {
    /**
     * Checksums are stored in the database as a string with the format type:checksum and are comma separated.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ChecksumConverter.class);

    @Override
    public String convertToDatabaseColumn(List<Checksum> checksums) {
        if (checksums != null && !checksums.isEmpty()) {
            try {
                return checksums.stream().map(checksum -> checksum.getType().trim() + ":" + checksum.getChecksum().trim()).collect(Collectors.joining(","));
            } catch (NullPointerException ex) {
                LOG.error("Could not convert checksum(s) to string for database", ex);
                return null;
            }
        }
        return null;
    }

    @Override
    public List<Checksum> convertToEntityAttribute(String checksumString) {
        List<Checksum> cs = new ArrayList<>();
        if (StringUtils.isNotEmpty(checksumString)) {
            String[] checksumsArray = checksumString.split(",");
            try {
                for (String s : checksumsArray) {
                    cs.add(new Checksum(s.split(":")[0].trim(), s.split(":")[1].trim()));
                }
                return cs;
            } catch (ArrayIndexOutOfBoundsException ex) {
                LOG.error("Could not parse checksum(s) " + checksumString, ex);
                return null;
            }
        }
        return null;
    }
}
