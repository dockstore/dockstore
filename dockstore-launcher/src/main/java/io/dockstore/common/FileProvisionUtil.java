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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.dockstore.provision.ProgressPrinter;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.Util;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;

/**
 * Created by dyuen on 2/21/17.
 */
public final class FileProvisionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileProvisionUtil.class);

    private FileProvisionUtil() {
        // disable utility constructor
    }

    static void downloadFromVFS2(String path, String targetFilePath) {
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
     * Copy from stream to stream while displaying progress
     *
     * @param inputStream source
     * @param inputSize   total size
     * @param outputSteam destination
     * @throws IOException  throws an exception if unable to provision input files
     */
    static void copyFromInputStreamToOutputStream(InputStream inputStream, long inputSize, OutputStream outputSteam)
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

    public static PluginManager getPluginManager(INIConfiguration config) {
        String filePluginLocation = getFilePluginLocation(config);
        // create plugin directory if it does not exist
        Path path = Paths.get(filePluginLocation);
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                throw new RuntimeException("Could not create plugin directory", e);
            }
        }
        // need to systematically clean up old versions of plugins
        VersionAwarePluginManager versionCleaner = new VersionAwarePluginManager(new File(filePluginLocation));
        versionCleaner.cleanupOldVersions();
        // start a regular plugin manager to interact with plugins
        PluginManager pluginManager = new VersionAwarePluginManager(new File(filePluginLocation));
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        return pluginManager;
    }

    public static String getFilePluginLocation(INIConfiguration config) {
        String userHome = System.getProperty("user.home");
        String pluginPath = userHome + File.separator + ".dockstore" + File.separator + "plugins";
        return config.getString("file-plugins-location", pluginPath);
    }

    public static void downloadPlugins(INIConfiguration configFile) {
        String filePluginLocation = FileProvisionUtil.getFilePluginLocation(configFile);
        // get sections with versions

        // get plugin versions and default versions if not available

        // download versions info filePluginLocation
        String template = "https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/%2$s/%1$s/%2$s-%1$s.zip";
        try {
            downloadPlugin(filePluginLocation, template, "0.0.6", "dockstore-file-icgc-storage-client-plugin");
            downloadPlugin(filePluginLocation, template, "0.0.3", "dockstore-file-s3-plugin");
            downloadPlugin(filePluginLocation, template, "0.0.5", "dockstore-file-synapse-plugin");
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadPlugin(String filePluginLocation, String template, String version, String pluginName)
            throws MalformedURLException, URISyntaxException {
        URL icgcUrl = new URI(String.format(template, version, pluginName)).toURL();
        Path pluginPath = Paths.get(filePluginLocation, String.format("%2$s-%1$s.zip", version,
                pluginName));
        String icgcLocation = icgcUrl.toString();
        String pluginLocation = pluginPath.toString();
        if (Files.exists(pluginPath)) {
            System.out.println("Skipping " + pluginLocation + ", already exists");
        } else {
            System.out.println("Downloading " + icgcLocation + " to " + pluginLocation);
            FileProvisionUtil.downloadFromVFS2(icgcLocation, pluginLocation);
        }
    }
}
