package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts the contents of a zip file into a <code>List</code> of <code>SourceFile</code>s, ensuring that
 * no zip exploits (e.g., zip bomb, path traversal) can execute
 */
public class ZipSourceFileHelper {

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

    public static List<SourceFile> sourceFilesFromZip(ZipFile zipFile) {
        Optional<? extends ZipEntry> dockstoreYml = zipFile.stream().filter(zipEntry -> ".dockstore.yml".equals(((ZipEntry) zipEntry).getName()) || ".dockstore.yaml".equals(((ZipEntry) zipEntry).getName())).findFirst();
        if (!dockstoreYml.isPresent()) {
            throw new CustomWebApplicationException("Missing .dockstore.yml or .dockstore.yaml", HttpStatus.SC_BAD_REQUEST);
        }
        final Yaml yaml = new Yaml();
        Map<String, Object> map = null;
        try {
            map = yaml.load(zipFile.getInputStream(dockstoreYml.get()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Object primaryDescriptorName = map.get("primaryDescriptor");
        if (primaryDescriptorName instanceof String) {
            Optional<? extends ZipEntry> primaryDescriptor = zipFile.stream().filter(zipEntry -> primaryDescriptorName.equals(((ZipEntry) zipEntry).getName())).findFirst();
            if (!primaryDescriptor.isPresent()) {
                throw new CustomWebApplicationException("Primary Descriptor missing " + primaryDescriptorName, HttpStatus.SC_BAD_REQUEST);
            }
            return
                    zipFile.stream().map(zipEntry -> {
                        SourceFile sourceFile = new SourceFile();
                        if (((ZipEntry) zipEntry).getName().equals(primaryDescriptorName)){
                            sourceFile.setType(SourceFile.FileType.DOCKSTORE_CWL);
                        } else {
//                            sourceFile.setType(SourceFile.FileType.);
                        }

                        return sourceFile;
                    }).collect(Collectors.toList());
        }
        else {
            throw new CustomWebApplicationException("Blah", HttpStatus.SC_BAD_REQUEST);
        }

    }
    private String getContent(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }

}
