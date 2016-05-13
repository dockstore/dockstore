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

import com.amazonaws.ClientConfiguration;
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
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.Util;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;

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

    private HierarchicalINIConfiguration config;
    private final Optional<OutputStream> stdoutStream;
    private final Optional<OutputStream> stderrStream;

    /**
     * Constructor
     */
    public FileProvisioning(String configFile) {
        // do not forward stdout and stderr
        stdoutStream = Optional.absent();
        stderrStream = Optional.absent();

        try {
            this.config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
    }

    // Which functions to move here? DCC and apache commons ones?
    private String getStorageClient() {
        return config.getString(DCC_CLIENT_KEY, "/icgc/dcc-storage/bin/dcc-storage-client");
    }

    private void downloadFromDccStorage(String objectId, String downloadDir, File downloadDirFileObj, String targetFilePath) {
        // default layout saves to original_file_name/object_id
        // file name is the directory and object id is actual file name
        String client = getStorageClient();
        String bob =
                client + " --quiet" + " download" + " --object-id " + objectId + " --output-dir " + downloadDir + " --output-layout id";
        Utilities.executeCommand(bob);

        // downloaded file
        String downloadPath = downloadDirFileObj.getAbsolutePath() + "/" + objectId;
        System.out.println("download path: " + downloadPath);
        File downloadedFileFileObj = new File(downloadPath);
        File targetPathFileObj = new File(targetFilePath);
        try {
            Files.move(downloadedFileFileObj, targetPathFileObj);
        } catch (IOException ioe) {
            LOG.error(ioe.getMessage());
            throw new RuntimeException("Could not move input file: ", ioe);
        }
    }

    private void downloadFromS3(String path, String targetFilePath) {
        AmazonS3 s3Client = getAmazonS3Client(config);
        String trimmedPath = path.replace("s3://", "");
        List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
        String bucketName = splitPathList.remove(0);

        S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
        try {
            FileOutputStream outputStream = new FileOutputStream(new File(targetFilePath));
            S3ObjectInputStream inputStream = object.getObjectContent();
            long inputSize = object.getObjectMetadata().getContentLength();
            copyFromInputStreamToOutputStream(inputStream, inputSize, outputStream);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RuntimeException("Could not provision input files from S3", e);
        }
    }

    private static AmazonS3 getAmazonS3Client(HierarchicalINIConfiguration config) {
        AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
        if (config.containsKey(S3_ENDPOINT)) {
            final String endpoint = config.getString(S3_ENDPOINT);
            LOG.info("found custom S3 endpoint, setting to {}", endpoint);
            s3Client.setEndpoint(endpoint);
            s3Client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
        }
        return s3Client;
    }

    private void downloadFromHttp(String path, String targetFilePath) {
        // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
        // https://commons.apache.org/proper/commons-vfs/filesystems.html
        try {
            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
            FileSystemManager fsManager = VFS.getManager();
            org.apache.commons.vfs2.FileObject src = fsManager.resolveFile(path);
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

    public void provisionInputFile(String path, String downloadDirectory, File downloadDirFileObj, String targetFilePath,
            PathInfo pathInfo) {
        if (pathInfo.isObjectIdType()) {
            String objectId = pathInfo.getObjectId();
            this.downloadFromDccStorage(objectId, downloadDirectory, downloadDirFileObj, targetFilePath);
        } else if (path.startsWith("s3://")) {
            this.downloadFromS3(path, targetFilePath);
        } else if (!pathInfo.isLocalFileType()) {
            this.downloadFromHttp(path, targetFilePath);
        }
    }

    public void provisionOutputFile(FileInfo file, String cwlOutputPath) {
        File sourceFile = new File(cwlOutputPath);
        long inputSize = sourceFile.length();
        if (file.getUrl().startsWith("s3://")) {
            AmazonS3 s3Client = FileProvisioning.getAmazonS3Client(config);
            String trimmedPath = file.getUrl().replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), sourceFile);
            putObjectRequest.setGeneralProgressListener(new ProgressListener() {
                ProgressPrinter printer = new ProgressPrinter();
                long runningTotal = 0;
                @Override public void progressChanged(ProgressEvent progressEvent) {
                    if (progressEvent.getEventType() == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT){
                        runningTotal += progressEvent.getBytesTransferred();
                    }
                    printer.handleProgress(runningTotal, inputSize);
                }
            });
            try {
                s3Client.putObject(putObjectRequest);
            } finally{
                System.out.println();
            }
        } else {
            try {
                FileSystemManager fsManager;
                // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                fsManager = VFS.getManager();
                // check for a local file path
                FileObject dest = fsManager.resolveFile(file.getUrl());
                FileObject src = fsManager.resolveFile(sourceFile.getAbsolutePath());
                copyFromInputStreamToOutputStream(src.getContent().getInputStream(), inputSize, dest.getContent().getOutputStream());
            } catch (IOException e) {
                throw new RuntimeException("Could not provision output files", e);
            }
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
                /** don't bother refreshing if no progress made */
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
            builder.append(percentage.setScale(0,BigDecimal.ROUND_HALF_EVEN).toPlainString()).append("%");

            System.out.print(builder);
            // track progress
            printedBefore = true;
            progress = fraction;
        }
    }

    /**
     * Copy from stream to stream while displaying progress
     *
     * @param inputStream source
     * @param inputSize   total size
     * @param outputSteam destination
     * @throws IOException
     */
    private static void copyFromInputStreamToOutputStream(InputStream inputStream, long inputSize, OutputStream outputSteam)
            throws IOException {
        CopyStreamListener listener = new CopyStreamListener() {
            ProgressPrinter printer = new ProgressPrinter();

            @Override public void bytesTransferred(CopyStreamEvent event) {
                /** do nothing */
            }

            @Override public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
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

    public static class PathInfo {
        private static final Logger LOG = LoggerFactory.getLogger(PathInfo.class);
        static final String DCC_STORAGE_SCHEME = "icgc";
        private boolean objectIdType;
        private String objectId = "";
        private boolean localFileType = false;

        boolean isObjectIdType() {
            return objectIdType;
        }

        String getObjectId() {
            return objectId;
        }

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

        public boolean isLocalFileType() {
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

