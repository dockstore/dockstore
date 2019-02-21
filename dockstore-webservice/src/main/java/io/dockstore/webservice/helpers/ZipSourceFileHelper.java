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

    /**
     * Converts the values in a zip into a list
     * @param zipFile
     * @param workflowFileType
     * @return
     */
    public static SourceFiles sourceFilesFromZip(ZipFile zipFile, SourceFile.FileType workflowFileType) {
        Map<String, Object> dockstoreYml = readDockstoreYml(zipFile);
        Object primaryDescriptorName = dockstoreYml.get("primaryDescriptor");
        List<String> testParameterFiles = (List<String>)dockstoreYml.get("testParameterFiles");
        if (primaryDescriptorName instanceof String) {
            String theName = (String)primaryDescriptorName;
            checkWorkflowType(workflowFileType, theName);
            ZipEntry primaryDescriptor = zipFile
                    .stream().
                    filter(zipEntry -> theName.equals(zipEntry.getName()))
                    .findFirst()
                    .orElseThrow(() -> new CustomWebApplicationException("Primary descriptor missing: " + theName,
                            HttpStatus.SC_BAD_REQUEST));
            final List<SourceFile> sourceFiles = zipFile.stream().map(zipEntry -> {
                SourceFile sourceFile = new SourceFile();
                if (testParameterFiles != null && testParameterFiles.contains(zipEntry.getName())) {
                    sourceFile.setType(paramFileType(workflowFileType));
                } else {
                    sourceFile.setType(workflowFileType);
                }
                sourceFile.setPath(zipEntry.getName());
                sourceFile.setAbsolutePath(zipEntry.getName());
                sourceFile.setContent(getContent(zipFile, zipEntry));
                return sourceFile;
            }).filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return new SourceFiles(
                    // Guaranteed to find primary descriptor, or we would have thrown, above
                    sourceFiles.stream().filter(sf -> sf.getPath().equals(primaryDescriptor.getName())).findFirst().get(),
                    sourceFiles
            );
        } else {
            throw new CustomWebApplicationException("Invalid or no primary descriptor specified in .dockstore.yml", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static void checkWorkflowType(SourceFile.FileType workflowFileType, String theName) {
        switch (workflowFileType) {
        case DOCKSTORE_CWL:
            if (!theName.toLowerCase().endsWith(".cwl")) {
               throw new CustomWebApplicationException("Zip file does not have a CWL primary descriptor", HttpStatus.SC_BAD_REQUEST);
            }
            break;
        case DOCKSTORE_WDL:
            if (!theName.toLowerCase().endsWith(".wdl")) {
                throw new CustomWebApplicationException("Zip file does not have a WDL primary descriptor", HttpStatus.SC_BAD_REQUEST);
            }
            break;
        default:
            // Other languages unsupported for now
            throw new CustomWebApplicationException("Unsupported workflow type: " + workflowFileType.toString(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static SourceFile.FileType paramFileType(SourceFile.FileType workFileType) {
        switch (workFileType) {
        case DOCKSTORE_CWL:
            return SourceFile.FileType.CWL_TEST_JSON;
        case DOCKSTORE_WDL:
            return SourceFile.FileType.WDL_TEST_JSON;
        case DOCKERFILE:
            return SourceFile.FileType.NEXTFLOW_TEST_PARAMS;
        default:
            throw new CustomWebApplicationException("Fix me", HttpStatus.SC_BAD_REQUEST);
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

    public static class SourceFiles {
        private final SourceFile primaryDescriptor;
        private final List<SourceFile> allDescriptors;

        public SourceFiles(SourceFile primaryDescriptor, List<SourceFile> allDescriptors) {
            this.primaryDescriptor = primaryDescriptor;
            this.allDescriptors = allDescriptors;
        }

        public SourceFile getPrimaryDescriptor() {
            return primaryDescriptor;
        }

        public List<SourceFile> getAllDescriptors() {
            return allDescriptors;
        }
    }

}
