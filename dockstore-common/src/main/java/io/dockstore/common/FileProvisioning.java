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
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.internal.S3Signer;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * The purpose of this class is to provide general functions to deal with workflow file provisioning.
 * Created by aduncan on 10/03/16.
 */
public class FileProvisioning {

        static {
                SignerFactory.registerSigner("S3Signer", S3Signer.class);
        }

        private static final Logger LOG = LoggerFactory.getLogger(FileProvisioning.class);

        public static final String S3_ENDPOINT = "s3.endpoint";
        public static final String DCC_CLIENT_KEY = "dcc_storage.client";

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
        public String getStorageClient() {
                return config.getString(DCC_CLIENT_KEY, "/icgc/dcc-storage/bin/dcc-storage-client");
        }

        public void downloadFromDccStorage(String objectId, String downloadDir, File downloadDirFileObj, String targetFilePath) {
                // default layout saves to original_file_name/object_id
                // file name is the directory and object id is actual file name
                String client = getStorageClient();
                String bob = new StringBuilder().append(client).append(" --quiet").append(" download").append(" --object-id ").append(objectId).append(" --output-dir ").append(downloadDir).append(" --output-layout id").toString();
                Utilities.executeCommand(bob, stdoutStream, stderrStream);

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

        public void downloadFromS3(String path, String targetFilePath) {
                AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
                if (config.containsKey(S3_ENDPOINT)) {
                        final String endpoint = config.getString(S3_ENDPOINT);
                        LOG.info("found custom S3 endpoint, setting to {}", endpoint);
                        s3Client.setEndpoint(endpoint);
                        s3Client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
                }
                String trimmedPath = path.replace("s3://", "");
                List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
                String bucketName = splitPathList.remove(0);

                S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
                try {
                        FileUtils.copyInputStreamToFile(object.getObjectContent(), new File(targetFilePath));
                } catch (IOException e) {
                        LOG.error(e.getMessage());
                        throw new RuntimeException("Could not provision input files from S3", e);
                }
        }

        public void downloadFromHttp(String path, String targetFilePath) {
                // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                // https://commons.apache.org/proper/commons-vfs/filesystems.html
                FileSystemManager fsManager;
                try {
                        // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                        fsManager = VFS.getManager();
                        org.apache.commons.vfs2.FileObject src = fsManager.resolveFile(path);
                        org.apache.commons.vfs2.FileObject dest = fsManager.resolveFile(new File(targetFilePath).getAbsolutePath());
                        dest.copyFrom(src, Selectors.SELECT_SELF);
                } catch (org.apache.commons.vfs2.FileSystemException e) {
                        LOG.error(e.getMessage());
                        throw new RuntimeException("Could not provision input files", e);
                }
        }

        public static class PathInfo {
                private static final Logger LOG = LoggerFactory.getLogger(PathInfo.class);
                public static final String DCC_STORAGE_SCHEME = "icgc";
                private boolean objectIdType;
                private String objectId = "";
                private boolean localFileType = false;

                public boolean isObjectIdType() {
                        return objectIdType;
                }

                public String getObjectId() {
                        return objectId;
                }

                public PathInfo(String path) {
                        try {
                                URI objectIdentifier = URI.create(path);	// throws IllegalArgumentException if it isn't a valid URI
                                if (objectIdentifier.getScheme() == null){
                                        localFileType = true;
                                }
                                if (objectIdentifier.getScheme().equalsIgnoreCase(DCC_STORAGE_SCHEME)) {
                                        objectIdType = true;
                                        objectId = objectIdentifier.getSchemeSpecificPart().toLowerCase();
                                }
                        } catch (IllegalArgumentException | NullPointerException iae) {
                                // if there is no scheme, then it must be a local file
                                LOG.warn("Invalid path specified for CWL pre-processor values: " + path);
                                objectIdType = false;
                        }
                }

                public boolean isLocalFileType() {
                        return localFileType;
                }
        }
}

