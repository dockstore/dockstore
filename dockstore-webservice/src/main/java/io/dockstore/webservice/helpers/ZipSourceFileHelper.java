package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Converts the contents of a zip file into a <code>List</code> of <code>SourceFile</code>s, ensuring that
 * no zip exploits (e.g., zip bomb, path traversal) can execute
 */
public final class ZipSourceFileHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ZipSourceFileHelper.class);

    private ZipSourceFileHelper() {
    }

    public static void validateZip(ZipFile zipFile, int maxEntries, long maxSize) {
        if (zipFile.stream().limit(maxEntries + 1).count() > maxEntries) {
            throw new CustomWebApplicationException("Too many entries in the zip", HttpStatus.SC_BAD_REQUEST);
        }
        final Optional<Long> uncompressedSize = zipFile.stream().map(z -> z.getSize()).reduce(Long::sum);
        uncompressedSize.ifPresent(s -> {
            if (s > maxSize) {
                throw new CustomWebApplicationException("Zip contents too large", HttpStatus.SC_BAD_REQUEST);
            }
        });
    }

    public static List<SourceFile> sourceFilesFromZip(ZipFile zipFile, SourceFile.FileType workflowFileType) {
        Map<String, Object> dockstoreYml = readDockstoreYml(zipFile);
        Object primaryDescriptorName = dockstoreYml.get("primaryDescriptor");
        List<String> testParameterFiles = (List<String>)dockstoreYml.get("testParameterFiles");
        if (primaryDescriptorName instanceof String) {
            ZipEntry primaryDescriptor = zipFile
                    .stream().
                    filter(zipEntry -> primaryDescriptorName.equals(zipEntry.getName()))
                    .findFirst()
                    .orElseThrow(() -> new CustomWebApplicationException("Primary descriptor missing: " + primaryDescriptorName,
                            HttpStatus.SC_BAD_REQUEST));
            return zipFile
                    .stream()
                    .map(zipEntry -> {
                        SourceFile sourceFile = new SourceFile();
                        if (testParameterFiles != null && testParameterFiles.contains(zipEntry.getName())) {
                            sourceFile.setType(SourceFile.FileType.CWL_TEST_JSON);
                        } else {
                            sourceFile.setType(SourceFile.FileType.DOCKSTORE_CWL);
                        }
                        sourceFile.setPath(zipEntry.getName());
                        sourceFile.setAbsolutePath(zipEntry.getName());
                        sourceFile.setContent(getContent(zipFile, zipEntry));
                        return sourceFile;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            throw new CustomWebApplicationException("No primary descriptor specified in .dockstore.yml", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static String getContent(ZipFile zipFile, ZipEntry zipEntry)  {
        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Error reading zip entry " + zipEntry.getName(), e);
            return null;
        }
    }

    private static Map<String, Object> readDockstoreYml(ZipFile zipFile) {
        ZipEntry dockstoreYml = zipFile.stream()
                .filter(zipEntry -> ".dockstore.yml".equals(zipEntry.getName()))
                .findFirst()
                .orElseThrow(() -> new CustomWebApplicationException("Missing .dockstore.yml", HttpStatus.SC_BAD_REQUEST));
        final Yaml yaml = new Yaml();
        try {
            return yaml.load(zipFile.getInputStream(dockstoreYml));
        } catch (IOException e) {
            LOG.error("Error reading .dockstore.yml", e);
            throw new CustomWebApplicationException("Invalid syntax in .dockstore.yml", HttpStatus.SC_BAD_REQUEST);
        }
    }

}
