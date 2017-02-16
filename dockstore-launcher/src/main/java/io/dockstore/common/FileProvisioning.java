/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.dockstore.provision.ProvisionInterface;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.Util;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginManager;

/**
 * The purpose of this class is to provide general functions to deal with workflow file provisioning.
 * Created by aduncan on 10/03/16.
 */
public class FileProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(FileProvisioning.class);

    private static final String DCC_CLIENT_KEY = "dcc_storage.client";
    private final List<ProvisionInterface> plugins;

    private INIConfiguration config;

    /**
     * Constructor
     */
    public FileProvisioning(String configFile) {
        this.config = Utilities.parseConfig(configFile);
        String filePluginLocation = this.config.getString("file-plugins-location", "/home/dyuen/.dockstore/plugins");
        PluginManager pluginManager = new DefaultPluginManager(new File(filePluginLocation));
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        this.plugins = pluginManager.getExtensions(ProvisionInterface.class);
        // set configuration
        for (ProvisionInterface provision : plugins) {
            SubnodeConfiguration section = config.getSection("file-" + provision.getClass().getName());
            Map<String, String> sectionConfig = new HashMap<>();
            Iterator<String> keys = section.getKeys();
            keys.forEachRemaining(key -> sectionConfig.put(key, section.getString(key)));
            provision.setConfiguration(sectionConfig);
        }
    }

    // Which functions to move here? DCC and apache commons ones?
    private String getStorageClient() {
        return config.getString(DCC_CLIENT_KEY, "/icgc/dcc-storage/bin/dcc-storage-client");
    }

    private void downloadFromDccStorage(String objectId, String downloadDir, String targetFilePath) {
        // default layout saves to original_file_name/object_id
        // file name is the directory and object id is actual file name
        String client = getStorageClient();
        String bob =
                client + " --quiet" + " download" + " --object-id " + objectId + " --output-dir " + downloadDir + " --output-layout id";
        Utilities.executeCommand(bob);

        // downloaded file
        String downloadPath = new File(downloadDir).getAbsolutePath() + "/" + objectId;
        System.out.println("download path: " + downloadPath);
        Path downloadedFileFileObj = Paths.get(downloadPath);
        Path targetPathFileObj = Paths.get(targetFilePath);
        try {
            Files.move(downloadedFileFileObj, targetPathFileObj);
        } catch (IOException ioe) {
            LOG.error(ioe.getMessage());
            throw new RuntimeException("Could not move input file: ", ioe);
        }
    }

    private void downloadFromHttp(String path, String targetFilePath) {
        // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
        // https://commons.apache.org/proper/commons-vfs/filesystems.html
        try {
            // force passive mode for FTP (see emails from Keiran)
            FileSystemOptions opts = new FileSystemOptions();
            FtpFileSystemConfigBuilder.getInstance().setPassiveMode(opts, true);

            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
            FileSystemManager fsManager = VFS.getManager();
            org.apache.commons.vfs2.FileObject src = fsManager.resolveFile(path, opts);
            org.apache.commons.vfs2.FileObject dest = fsManager.resolveFile(new File(targetFilePath).getAbsolutePath());
            InputStream inputStream = src.getContent().getInputStream();
            long inputSize = src.getContent().getSize();
            OutputStream outputSteam = dest.getContent().getOutputStream();
            copyFromInputStreamToOutputStream(inputStream, inputSize, outputSteam);
            // dest.copyFrom(src, Selectors.SELECT_SELF);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RuntimeException("Could not provision input files", e);
        }
    }

    /**
     * This method downloads both local and remote files into the working directory
     *
     * @param targetPath path for target file
     * @param localPath  the absolute path where we will download files to
     * @param pathInfo   additional information on the type of file
     */
    public void provisionInputFile(String targetPath, Path localPath, PathInfo pathInfo) {

        Path potentialCachedFile = null;
        final boolean useCache = isCacheOn(config);
        // check if a file exists in the cache and if it does, link/copy it into place
        if (useCache) {
            // check cache for cached files
            final String cacheDirectory = getCacheDirectory(config);
            // create cache directory
            final Path cachePath = Paths.get(cacheDirectory);
            if (Files.notExists(cachePath)) {
                if (!cachePath.toFile().mkdirs()) {
                    throw new RuntimeException("Could not create dockstore cache: " + cacheDirectory);
                }
            }

            final String sha1 = DigestUtils.sha1Hex(targetPath);
            final String sha1Prefix = sha1.substring(0, 2);
            final String sha1Suffix = sha1.substring(2);
            potentialCachedFile = Paths.get(cacheDirectory, sha1Prefix, sha1Suffix);
            if (Files.exists(potentialCachedFile)) {
                System.out.println("Found file " + targetPath + " in cache, hard-linking");
                try {
                    final Path parentPath = localPath.getParent();
                    if (Files.notExists(parentPath)) {
                        Files.createDirectory(parentPath);
                    }
                    Files.createLink(localPath, potentialCachedFile);
                } catch (IOException e) {
                    LOG.error("Cannot create hard link to cached file, you may want to move your cache", e.getMessage());
                    try {
                        Files.copy(potentialCachedFile, localPath);
                    } catch (IOException e1) {
                        LOG.error("Could not copy " + targetPath + " to " + localPath, e);
                        throw new RuntimeException("Could not copy " + targetPath + " to " + localPath, e1);
                    }
                    System.out.println("Found file " + targetPath + " in cache, copied");
                }
            }
        }

        // if a file does not exist yet, get it
        if (!Files.exists(localPath)) {
            // check if we can use a plugin
            boolean handledViaPlugin = false;
            for (ProvisionInterface provision : plugins) {
                if (provision.prefixHandled(targetPath)) {
                    provision.downloadFrom(targetPath, localPath);
                    handledViaPlugin = true;
                }
            }

            if (handledViaPlugin) {
                // do nothing
                LOG.info("Transfer already done via plugin");
            } else if (pathInfo.isObjectIdType()) {
                String objectId = pathInfo.getObjectId();
                this.downloadFromDccStorage(objectId, localPath.getParent().toFile().getAbsolutePath(), localPath.toFile().getAbsolutePath());
            } else if (!pathInfo.isLocalFileType()) {
                this.downloadFromHttp(targetPath, localPath.toFile().getAbsolutePath());
            } else {
                assert (pathInfo.isLocalFileType());
                // hard link into target location
                Path actualTargetPath = null;
                try {
                    String workingDir = System.getProperty("user.dir");
                    if (targetPath.startsWith("/")) {
                        // absolute path
                        actualTargetPath = Paths.get(targetPath);
                    } else {
                        // relative path
                        actualTargetPath = Paths.get(workingDir, targetPath);
                    }
                    // create needed directories
                    File parentFile = localPath.toFile().getParentFile();
                    if (!parentFile.exists() && !parentFile.mkdirs()) {
                        throw new IOException("Could not create " + localPath);
                    }

                    // create link
                    Files.createLink(localPath, actualTargetPath);
                } catch (IOException e) {
                    LOG.info("Could not link " + targetPath + " to " + localPath + " , copying instead", e);
                    try {
                        Files.copy(actualTargetPath, localPath);
                    } catch (IOException e1) {
                        LOG.error("Could not copy " + targetPath + " to " + localPath, e);
                        throw new RuntimeException("Could not copy " + targetPath + " to " + localPath, e1);
                    }
                }
            }
        }

        // cache the file if we got it successfully
        if (useCache) {
            // populate cache
            if (Files.notExists(potentialCachedFile)) {
                System.out.println("Caching file " + localPath + " in cache, hard-linking");
                try {
                    // create parent directory
                    final Path parentPath = potentialCachedFile.getParent();
                    if (Files.notExists(parentPath)) {
                        Files.createDirectory(parentPath);
                    }
                    Files.createLink(potentialCachedFile, localPath);
                } catch (IOException e) {
                    LOG.error("Cannot create hard link for local file, skipping", e);
                }
            }
        }
    }

    public static String getCacheDirectory(INIConfiguration config) {
        return config.getString("cache-dir", System.getProperty("user.home") + File.separator + ".dockstore" + File.separator + "cache");
    }

    private static boolean isCacheOn(INIConfiguration config) {
        final String useCache = config.getString("use-cache", "false");
        return "true".equalsIgnoreCase(useCache) || "use".equalsIgnoreCase(useCache) || "T".equalsIgnoreCase(useCache);
    }

    /**
     * Copies files from srcPath to destPath
     *
     * @param srcPath  source file
     * @param destPath destination file
     */
    public void provisionOutputFile(String srcPath, String destPath) {
        File sourceFile = new File(srcPath);
        for (ProvisionInterface provision : plugins) {
            if (provision.prefixHandled(destPath)) {
                provision.uploadTo(destPath, Paths.get(srcPath), null);
                return;
            }
        }
        try {
            long inputSize = sourceFile.length();
            FileSystemManager fsManager;
            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
            fsManager = VFS.getManager();
            // check for a local file path
            Path currentWorkingDir = Paths.get("").toAbsolutePath();
            FileObject dest = fsManager.resolveFile(currentWorkingDir.toFile(), destPath);
            FileObject src = fsManager.resolveFile(sourceFile.getAbsolutePath());
            copyFromInputStreamToOutputStream(src.getContent().getInputStream(), inputSize, dest.getContent().getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not provision output files", e);
        }
    }

    /**
     * Copy from stream to stream while displaying progress
     *
     * @param inputStream source
     * @param inputSize   total size
     * @param outputSteam destination
     * @throws IOException  throws an exception if unable to provision input files
     */
    private static void copyFromInputStreamToOutputStream(InputStream inputStream, long inputSize, OutputStream outputSteam)
            throws IOException {
        CopyStreamListener listener = new CopyStreamListener() {
            ProgressPrinter printer = new ProgressPrinter();

            @Override
            public void bytesTransferred(CopyStreamEvent event) {
                /* do nothing */
            }

            @Override
            public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                printer.handleProgress(totalBytesTransferred, streamSize);
            }
        };
        try (OutputStream outputStream = outputSteam) {
            // a larger buffer improves copy performance
            // we can also split this (local file copy) out into a plugin later
            final int largeBuffer = 100;
            Util.copyStream(inputStream, outputStream, Util.DEFAULT_COPY_BUFFER_SIZE * largeBuffer, inputSize, listener);
        } catch (IOException e) {
            throw new RuntimeException("Could not provision input files", e);
        } finally {
            IOUtils.closeQuietly(inputStream);
            System.out.println();
        }
    }

    public static void main(String[] args) {
        PluginManager pluginManager = new DefaultPluginManager(new File("dockstore-file-plugins/built"));
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<ProvisionInterface> greetings = pluginManager.getExtensions(ProvisionInterface.class);
        for (ProvisionInterface provision : greetings) {
            System.out.println(">>> " + provision.prefixHandled("test"));
        }

//        String userHome = System.getProperty("user.home");
//        FileProvisioning provisioning = new FileProvisioning(userHome + File.separator + ".dockstore" + File.separator + "config");
//        long firstTime = System.currentTimeMillis();
//        // used /home/dyuen/Downloads/pcawg_broad_public_refs_full.tar.gz for testing
//        provisioning.provisionOutputFile(args[0], args[0]);
//        final long millisecondsInSecond = 1000L;
//        System.out.println((System.currentTimeMillis() - firstTime) / millisecondsInSecond);
    }

    private static class ProgressPrinter {
        static final int SIZE_OF_PROGRESS_BAR = 50;
        boolean printedBefore = false;
        BigDecimal progress = new BigDecimal(0);

        void handleProgress(long totalBytesTransferred, long streamSize) {

            BigDecimal numerator = BigDecimal.valueOf(totalBytesTransferred);
            BigDecimal denominator = BigDecimal.valueOf(streamSize);
            BigDecimal fraction = numerator.divide(denominator, new MathContext(2, RoundingMode.HALF_EVEN));
            if (fraction.equals(progress)) {
                /* don't bother refreshing if no progress made */
                return;
            }

            BigDecimal outOfTwenty = fraction.multiply(new BigDecimal(SIZE_OF_PROGRESS_BAR));
            BigDecimal percentage = fraction.movePointRight(2);
            StringBuilder builder = new StringBuilder();
            if (printedBefore) {
                builder.append('\r');
            }

            builder.append("[");
            for (int i = 0; i < SIZE_OF_PROGRESS_BAR; i++) {
                if (i < outOfTwenty.intValue()) {
                    builder.append("#");
                } else {
                    builder.append(" ");
                }
            }

            builder.append("] ");
            builder.append(percentage.setScale(0, BigDecimal.ROUND_HALF_EVEN).toPlainString()).append("%");

            System.out.print(builder);
            // track progress
            printedBefore = true;
            progress = fraction;
        }
    }

    public static class PathInfo {
        static final String DCC_STORAGE_SCHEME = "icgc";

        private static final Logger LOG = LoggerFactory.getLogger(PathInfo.class);
        private boolean objectIdType;
        private String objectId = "";
        private boolean localFileType = false;

        public PathInfo(String path) {
            try {
                URI objectIdentifier = URI.create(path);    // throws IllegalArgumentException if it isn't a valid URI
                if (objectIdentifier.getScheme() == null) {
                    localFileType = true;
                }
                if (objectIdentifier.getScheme().equalsIgnoreCase(DCC_STORAGE_SCHEME)) {
                    objectIdType = true;
                    objectId = objectIdentifier.getSchemeSpecificPart().toLowerCase();
                }
            } catch (IllegalArgumentException | NullPointerException iae) {
                // if there is no scheme, then it must be a local file
                LOG.info("Invalid or local path specified for CWL pre-processor values: " + path);
                objectIdType = false;
            }
        }

        boolean isObjectIdType() {
            return objectIdType;
        }

        String getObjectId() {
            return objectId;
        }

        boolean isLocalFileType() {
            return localFileType;
        }
    }

    /**
     * Describes a single File
     */
    public static class FileInfo {
        private String localPath;
        private String url;

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}

