package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

/**
 * Converts the contents of a zip file into a <code>SourceFiles</code> object, ensuring that
 * no zip exploits (e.g., zip bomb, path traversal) can execute.
 *
 * It writes out the input stream to a local file. Unfortunately we can't get the compressed
 * sizes from the request body without saving it to a file first:
 * https://stackoverflow.com/questions/36045421/java-zipentry-getsize-returns-1
 *
 * For protection,
 * <ol>
 *     <li>Only write up to ZIP_SIZE_LIMIT of bytes to disk.</li>
 *     <li>Look at the the compressed sizes and also only allow up to ZIP_SIZE_LIMIT of bytes</li>
 *     <li>Also ensure that there are no more than ZIP_ENTRIES_LIMIT number of entries, e.g., so</li>
 * </ol>
 *
 */
public final class ZipSourceFileHelper {

    public static final int ZIP_SIZE_LIMIT = 100_000;
    public static final int ZIP_ENTRIES_LIMIT = 100;
    private static final Logger LOG = LoggerFactory.getLogger(ZipSourceFileHelper.class);

    private ZipSourceFileHelper() {
    }

    /**
     * Reads an input stream and converts it to a SourceFiles object. The input stream is expected to have the following
     * characteristics and the method will throw an exception if any of these conditions is not met.
     *
     * <ul>
     * <li>The input stream is zipped content</li>
     * <li>The zip content contains a .dockstore.yml (TBD define valid)</li>
     * <li>A valid .dockstore.yml in the root of the zip</li>
     * <li>The zip content, both compressed and uncompressed, does not exceed ZIP_SIZE_LIMIT.</li>
     * <li>The number of entries in the zip does not exceed ZIP_ENTRIES_LIMIT</li>
     * </ul>
     *
     * @param payload
     * @param fileType
     * @return a SourceFiles object
     * @throws CustomWebApplicationException if the size of the zip is greater than ZIP_SIZE_LIMIT
     * @throws CustomWebApplicationException if the zip has more than ZIP_ENTRIES_LIMIT files in it
     * @throws CustomWebApplicationException if the uncompressed size of the zip is greater than ZIP_SIZE_LIMIT
     * @throws CustomWebApplicationException if there is an error reading the zip, e.g., if the content is not a valid zip
     * @throws CustomWebApplicationException there is no valid .dockstore.yml in the zip
     */
    public static SourceFiles sourceFilesFromInputStream(InputStream payload, SourceFile.FileType fileType) {
        File tempDir = null;
        try {
            tempDir = Files.createTempDir();
            File tempZip = new File(tempDir, "workflow.zip");
            try (InputStream limitStream = ByteStreams.limit(payload, ZIP_SIZE_LIMIT + 1)) {
                FileUtils.copyToFile(limitStream, tempZip);
                if (tempZip.length() > ZIP_SIZE_LIMIT) {
                    throw new CustomWebApplicationException("Request body is too large", HttpStatus.SC_REQUEST_TOO_LONG);
                }
            }
            try (ZipFile zipFile = new ZipFile(tempZip)) {
                validateZip(zipFile, ZIP_ENTRIES_LIMIT, ZIP_SIZE_LIMIT);
                return sourceFilesFromZip(zipFile, fileType);
            }
        } catch (Exception e) {
            throw new CustomWebApplicationException("Error reading request", HttpStatus.SC_BAD_REQUEST);
        } finally {
            try {
                if (tempDir != null) {
                    FileUtils.deleteDirectory(tempDir);
                }
            } catch (IOException e) {
                LOG.error("Error deleting temp zip", e);
            }
        }

    }

    static void validateZip(ZipFile zipFile, int maxEntries, long maxSize) {
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
     *
     * @param zipFile
     * @param workflowFileType
     * @return
     */
    static SourceFiles sourceFilesFromZip(ZipFile zipFile, SourceFile.FileType workflowFileType) {
        DockstoreYaml dockstoreYml = readAndPrevalidateDockstoreYml(zipFile);
        final String primaryDescriptor = dockstoreYml.primaryDescriptor;
        List<String> testParameterFiles = dockstoreYml.testParameterFiles;
        if (primaryDescriptor != null) {
            checkWorkflowType(workflowFileType, primaryDescriptor);
            zipFile.stream()
                    .filter(zipEntry -> primaryDescriptor.equals(zipEntry.getName()))
                    .findFirst()
                    .orElseThrow(() -> new CustomWebApplicationException("Primary descriptor missing: " + primaryDescriptor, HttpStatus.SC_BAD_REQUEST));
            final List<SourceFile> sourceFiles = zipFile
                    .stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .map(zipEntry -> {
                        SourceFile sourceFile = new SourceFile();
                        if (testParameterFiles != null && testParameterFiles.contains(zipEntry.getName())) {
                            sourceFile.setType(paramFileType(workflowFileType));
                        } else if (".dockstore.yml".equals(zipEntry.getName())) {
                            sourceFile.setType(SourceFile.FileType.DOCKSTORE_YML);
                        } else {
                            sourceFile.setType(workflowFileType);
                        }
                        sourceFile.setPath(zipEntry.getName());
                        sourceFile.setAbsolutePath(addLeadingSlashIfNecessary(zipEntry.getName()));
                        sourceFile.setContent(getContent(zipFile, zipEntry));
                        return sourceFile;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
            return new SourceFiles(
                    // Guaranteed to find primary descriptor, or we would have thrown, above
                    sourceFiles.stream().filter(sf -> sf.getPath().equals(primaryDescriptor)).findFirst().get(), sourceFiles);
        } else {
            throw new CustomWebApplicationException("Invalid or no primary descriptor specified in .dockstore.yml",
                    HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static String addLeadingSlashIfNecessary(final String name) {
        if (name.startsWith("/")) {
            return name;
        }
        return "/" + name;
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

    private static String getContent(ZipFile zipFile, ZipEntry zipEntry) {
        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Error reading zip entry " + zipEntry.getName(), e);
            return null;
        }
    }

    private static DockstoreYaml readAndPrevalidateDockstoreYml(ZipFile zipFile) {
        ZipEntry dockstoreYml = zipFile.stream().filter(zipEntry -> ".dockstore.yml".equals(zipEntry.getName())).findFirst()
                .orElseThrow(() -> new CustomWebApplicationException("Missing .dockstore.yml", HttpStatus.SC_BAD_REQUEST));
        try {
            return readAndPrevalidateDockstoreYml(zipFile.getInputStream(dockstoreYml));
        } catch (IOException e) {
            LOG.error("Error reading .dockstore.yml", e);
            throw new CustomWebApplicationException("Invalid syntax in .dockstore.yml", HttpStatus.SC_BAD_REQUEST);
        }
    }

    // Should move this out of here when other components use dockstore.yml
    static DockstoreYaml readAndPrevalidateDockstoreYml(InputStream inputStream) {
        Constructor constructor = new Constructor(DockstoreYaml.class);
        constructor.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                if ("class".equals(name)) {
                    name = "clazz";
                }
                return super.getProperty(type, name);
            }

        });
        final Yaml yaml = new Yaml(constructor);
        try {
            DockstoreYaml dockstoreYaml = yaml.load(inputStream);
            if (!DockstoreYaml.VERSION.equals(dockstoreYaml.dockstoreVersion)) {
                throw  new CustomWebApplicationException("Invalid or missing dockstoreVersion in .dockstore.yml, expecting \"1.0\"", HttpStatus.SC_BAD_REQUEST);
            } else if (!DockstoreYaml.CLAZZ.equals(dockstoreYaml.clazz)) {
                throw new CustomWebApplicationException("Invalid or missing class in .dockstore.yml; expecting \"workflow\"", HttpStatus.SC_BAD_REQUEST);
            }
            return dockstoreYaml;
        } catch (CustomWebApplicationException ex) {
            throw ex;
        } catch (Exception e) {
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
