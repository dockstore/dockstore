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
package io.dockstore.provision;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

/**
 * @author dyuen
 */
public class S3Plugin extends Plugin {

    static {
        SignerFactory.registerSigner("S3Signer", S3Signer.class);
    }

    public S3Plugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        // for testing the development mode
        if (RuntimeMode.DEVELOPMENT.equals(wrapper.getRuntimeMode())) {
            System.out.println(StringUtils.upperCase("S3Plugin development mode"));
        }
    }

    @Override
    public void stop() {
        System.out.println("S3Plugin.stop()");
    }


    static ProgressListener getProgressListener(final long inputSize) {
        return new ProgressListener() {
            ProvisionInterface.ProgressPrinter printer = new ProvisionInterface.ProgressPrinter();
            long runningTotal = 0;
            @Override
            public void progressChanged(ProgressEvent progressEvent) {
                if (progressEvent.getEventType() == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT) {
                    runningTotal += progressEvent.getBytesTransferred();
                }
                printer.handleProgress(runningTotal, inputSize);
            }
        };
    }

    @Extension
    public static class S3Provision implements ProvisionInterface {

        static {
            SignerFactory.registerSigner("S3Signer", S3Signer.class);
        }

        private static final String S3_ENDPOINT = "endpoint";
        private Map<String, String> config;
        public void setConfiguration(Map<String, String> map) {
            this.config = map;
        }


        private AmazonS3 getAmazonS3Client() {
            AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
            if (config.containsKey(S3_ENDPOINT)) {
                final String endpoint = config.get(S3_ENDPOINT);
                System.err.println("found custom S3 endpoint, setting to " + endpoint);
                s3Client.setEndpoint(endpoint);
                s3Client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
            }
            return s3Client;
        }

        public boolean prefixHandled(String path) {
            return path.startsWith("s3://");
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            AmazonS3 s3Client = getAmazonS3Client();
            TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3Client).build();
            String trimmedPath = sourcePath.replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
            try {
                GetObjectRequest request = new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList));
                request.setGeneralProgressListener(getProgressListener(object.getObjectMetadata().getContentLength()));
                Download download = tx.download(request, destination.toFile());
                download.waitForCompletion();
                Transfer.TransferState state = download.getState();
                return state == Transfer.TransferState.Completed;
            } catch (SdkBaseException e) {
                throw new RuntimeException("Could not provision input files from S3", e);
            } catch (InterruptedException e) {
                System.err.println("Upload to S3 failed " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                tx.shutdownNow(true);
            }
        }

        public boolean uploadTo(String destPath, Path sourceFile, String metadata) {
            long inputSize = sourceFile.toFile().length();
            AmazonS3 s3Client = getAmazonS3Client();
            TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3Client).build();

            String trimmedPath = destPath.replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), sourceFile.toFile());
            putObjectRequest.setGeneralProgressListener(getProgressListener(inputSize));
            try {
                Upload upload = tx.upload(putObjectRequest);
                upload.waitForUploadResult();
                Transfer.TransferState state = upload.getState();
                return state == Transfer.TransferState.Completed;
            } catch (InterruptedException e) {
                System.err.println("Upload to S3 failed " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                tx.shutdownNow(true);
            }
        }

    }

}

