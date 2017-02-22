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
import java.io.FileOutputStream;
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
import java.util.List;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkBaseException;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.internal.S3Signer;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.Util;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The purpose of this class is to provide general functions to deal with workflow file provisioning.
 * Created by aduncan on 10/03/16.
 */
public class FileProvisioning {

    static {
        SignerFactory.registerSigner("S3Signer", S3Signer.class);
    }

    private static final Logger LOG = LoggerFactory.getLogger(FileProvisioning.class);

    private static final String S3_ENDPOINT = "s3.endpoint";
    private static final String DCC_CLIENT_KEY = "dcc_storage.client";

    private INIConfiguration config;

    /**
     * Constructor
     */
    public FileProvisioning(String configFile) {
        this.config = Utilities.parseConfig(configFile);
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

    private void downloadFromS3(String path, String targetFilePath) {
        AmazonS3 s3Client = getAmazonS3Client(config);
        TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3Client).build();
        String trimmedPath = path.replace("s3://", "");
        List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
        String bucketName = splitPathList.remove(0);

        S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
        try {
            GetObjectRequest request = new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList));
            request.setGeneralProgressListener(getProgressListener(object.getObjectMetadata().getContentLength()));
            Download download = tx.download(request, new File(targetFilePath));
            download.waitForCompletion();
        } catch (SdkBaseException e) {
            LOG.error(e.getMessage());
            throw new RuntimeException("Could not provision input files from S3", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally{
            tx.shutdownNow(true);
        }
    }

    private void downloadFromSynapse(String path, String targetFilePath) {
        SynapseClient synapseClient = new SynapseClientImpl();

        try {
            String synapseKey = config.getString("synapse-api-key");
            String synapseUserName = config.getString("synapse-user-name");
            synapseClient.setApiKey(synapseKey);
            synapseClient.setUserName(synapseUserName);
            synapseClient.downloadFromFileEntityCurrentVersion(path, new File(targetFilePath));
        } catch (Exception e) {
            LOG.error(e.getMessage());
            throw new RuntimeException("Could not provision input files from Synapse", e);
        }
    }

    private static AmazonS3 getAmazonS3Client(INIConfiguration config) {
        AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
        if (config.containsKey(S3_ENDPOINT)) {
            final String endpoint = config.getString(S3_ENDPOINT);
            LOG.info("found custom S3 endpoint, setting to {}", endpoint);
            s3Client.setEndpoint(endpoint);
            s3Client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
        }
        return s3Client;
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
            if (pathInfo.isObjectIdType()) {
                String objectId = pathInfo.getObjectId();
                this.downloadFromDccStorage(objectId, localPath.getParent().toFile().getAbsolutePath(), localPath.toFile().getAbsolutePath());
            } else if (targetPath.startsWith("syn")) {
                this.downloadFromSynapse(targetPath, localPath.toFile().getAbsolutePath());
            } else if (targetPath.startsWith("s3://")) {
                this.downloadFromS3(targetPath, localPath.toFile().getAbsolutePath());
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
        long inputSize = sourceFile.length();
        if (destPath.startsWith("s3://")) {
            AmazonS3 s3Client = FileProvisioning.getAmazonS3Client(config);
            String trimmedPath = destPath.replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), sourceFile);
            putObjectRequest.setGeneralProgressListener(new ProgressListener() {
                ProgressPrinter printer = new ProgressPrinter();
                long runningTotal = 0;

                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                    if (progressEvent.getEventType() == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT) {
                        runningTotal += progressEvent.getBytesTransferred();
                    }
                    printer.handleProgress(runningTotal, inputSize);
                }
            });
            try {
                s3Client.putObject(putObjectRequest);
            } finally {
                System.out.println();
            }
        } else {
            try {
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
    }

    /**
     * Copy from stream to stream while displaying progress
     *
     * @param inputStream source
     * @param inputSize   total size
     * @param outputSteam destination
     * @throws IOException throws an exception if unable to provision input files
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
            Util.copyStream(inputStream, outputStream, Util.DEFAULT_COPY_BUFFER_SIZE, inputSize, listener);
        } catch (IOException e) {
            throw new RuntimeException("Could not provision input files", e);
        } finally {
            IOUtils.closeQuietly(inputStream);
            System.out.println();
        }
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

    public static void main(String[] args){
        String userHome = System.getProperty("user.home");
        FileProvisioning provisioning = new FileProvisioning(userHome + File.separator + ".dockstore" + File.separator + "config");
        long firstTime = System.currentTimeMillis();
        // used /home/dyuen/Downloads/pcawg_broad_public_refs_full.tar.gz for testing
        provisioning.provisionOutputFile(args[0],args[0]);
        final long millisecondsInSecond = 1000L;
        System.out.println((System.currentTimeMillis() - firstTime)/millisecondsInSecond);
    }
}

