package io.dockstore.webservice.helpers;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.yaml.DockstoreYaml10;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts the contents of a zip file into a <code>SourceFiles</code> object, ensuring that
 * no zip exploits (e.g., zip bomb, path traversal) can execute.
 *
 * It writes out the input stream to a local file. Unfortunately we can't get the compressed
 * sizes from the request body without saving it to a file first:
 * <a href="https://stackoverflow.com/questions/36045421/java-zipentry-getsize-returns-1">...</a>
 *
 * For protection,
 * <ol>
 *     <li>Only write up to ZIP_SIZE_LIMIT of bytes to disk.</li>
 *     <li>Look at the compressed sizes and also only allow up to ZIP_SIZE_LIMIT of bytes</li>
 *     <li>Also ensure that there are no more than ZIP_ENTRIES_LIMIT number of entries, e.g., so</li>
 * </ol>
 *
 */
public final class ZipSourceFileHelper {

    private static final int ZIP_SIZE_LIMIT = 100_000;
    private static final int ZIP_ENTRIES_LIMIT = 100;
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
    public static SourceFiles sourceFilesFromInputStream(InputStream payload, DescriptorLanguage.FileType fileType) {
        File tempDir = null;
        try {
            tempDir = Files.createTempDir();
            File tempZip = new File(tempDir, "workflow.zip");
            // ByteStreams.limit limits the amount of bytes that can be read from the input stream. No matter how large the input
            // stream, only a max ZIP_SIZE_LIMIT + 1 bytes will be read, and only a max of ZIP_SIZE_LIMIT + 1 bytes will be written to disk.
            try (InputStream limitStream = ByteStreams.limit(payload, ZIP_SIZE_LIMIT + 1L)) {
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

    protected static void validateZip(ZipFile zipFile, int maxEntries, long maxSize) {
        if (zipFile.stream().limit(maxEntries + 1L).count() > maxEntries) {
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
     * Converts the values in a zip into a SourceFiles object
     *
     * @param zipFile
     * @param workflowFileType
     * @return
     */
    protected static SourceFiles sourceFilesFromZip(ZipFile zipFile, DescriptorLanguage.FileType workflowFileType) {
        DockstoreYaml10 dockstoreYml = readAndPrevalidateDockstoreYml(zipFile);
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
                            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_YML);
                        } else {
                            sourceFile.setType(workflowFileType);
                        }
                        sourceFile.setPath(zipEntry.getName());
                        sourceFile.setAbsolutePath(addLeadingSlashIfNecessary(zipEntry.getName()));
                        sourceFile.setContent(getContent(zipFile, zipEntry));
                        return sourceFile;
                    }).collect(Collectors.toList());
            return new SourceFiles(
                    // Guaranteed to find primary descriptor, or we would have thrown, above
                    sourceFiles.stream().filter(sf -> sf.getPath().equals(primaryDescriptor)).findFirst().get(), sourceFiles);
        } else {
            throw new CustomWebApplicationException("Invalid or no primary descriptor specified in .dockstore.yml",
                    HttpStatus.SC_BAD_REQUEST);
        }
    }

    public static String addLeadingSlashIfNecessary(final String name) {
        if (name.startsWith("/")) {
            return name;
        }
        return "/" + name;
    }

    private static void checkWorkflowType(DescriptorLanguage.FileType workflowFileType, String theName) {
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

    /**
     * Given a FileType for a descriptor, return the FileType of the corresponding parameter file.
     * @param workFileType a filetype for a descriptor
     * @return filetype of the parameter descriptor
     * @throws CustomWebApplicationException if workFileType is not the file type of a descriptor
     */
    private static DescriptorLanguage.FileType paramFileType(DescriptorLanguage.FileType workFileType) {
        switch (workFileType) {
        case DOCKSTORE_CWL:
            return DescriptorLanguage.FileType.CWL_TEST_JSON;
        case DOCKSTORE_WDL:
            return DescriptorLanguage.FileType.WDL_TEST_JSON;
        case NEXTFLOW:
            return DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS;
        default:
            throw new CustomWebApplicationException("", HttpStatus.SC_BAD_REQUEST);
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

    private static DockstoreYaml10 readAndPrevalidateDockstoreYml(final ZipFile zipFile) {
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
    protected static DockstoreYaml10 readAndPrevalidateDockstoreYml(final InputStream inputStream) {
        try {
            final String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            return DockstoreYamlHelper.readDockstoreYaml10(content);

        } catch (Exception ex) {
            final String msg = "Error reading .dockstore.yml: " + ex.getMessage();
            LOG.error(msg, ex);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
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
