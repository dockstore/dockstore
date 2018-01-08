/*
 *    Copyright 2017 OICR
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.dockstore.provision.ProgressPrinter;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.Util;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;

/**
 * @author dyuen
 * @since 2/21/17
 */
public final class FileProvisionUtil {

    static final String PLUGINS_JSON_FILENAME = "plugins.json";
    private static final Logger LOG = LoggerFactory.getLogger(FileProvisionUtil.class);

    private FileProvisionUtil() {
        // disable utility constructor
    }

    static boolean downloadFromVFS2(String path, Path targetFilePath, int threads) {
        // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
        // https://commons.apache.org/proper/commons-vfs/filesystems.html
        try {
            // force passive mode for FTP (see emails from Keiran)
            FileSystemOptions opts = new FileSystemOptions();
            FtpFileSystemConfigBuilder.getInstance().setPassiveMode(opts, true);

            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
            FileSystemManager fsManager = VFS.getManager();
            try (FileObject src = fsManager.resolveFile(path, opts);
                FileObject dest = fsManager.resolveFile(targetFilePath.toFile().getAbsolutePath())) {
                copyFromInputStreamToOutputStream(src, dest, threads);
            }
            return true;
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return false;
        }
    }

    /**
     * Copy from file object to file object while displaying progress, will not close streams
     *
     * @throws IOException throws an exception if unable to provision input files
     */
    static void copyFromInputStreamToOutputStream(FileObject src, FileObject dest, int threads) throws IOException {
        CopyStreamListener listener = new CopyStreamListener() {
            ProgressPrinter printer = new ProgressPrinter(threads, threads > 1 ? src.toString() : "");

            @Override
            public void bytesTransferred(CopyStreamEvent event) {
                /* do nothing */
            }

            @Override
            public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                synchronized (System.out) {
                    printer.handleProgress(totalBytesTransferred, streamSize);
                }
            }
        };
        System.out.println("Downloading: " + src.toString() + " to " + dest.toString());

        long size;
        try (FileContent srcContent = src.getContent()) {
            // odd concurrency issue
            size = srcContent.getSize();
        }

        try (FileContent srcContent = src.getContent();
            FileContent destContent = dest.getContent();
            InputStream inputStream = srcContent.getInputStream();
                OutputStream outputStream = destContent.getOutputStream()) {
            // a larger buffer improves copy performance
            // we can also split this (local file copy) out into a plugin later
            final int largeBuffer = 100;
            Util.copyStream(inputStream, outputStream, Util.DEFAULT_COPY_BUFFER_SIZE * largeBuffer, size, listener);
        } finally {
            // finalize output from the printer
            System.out.println();
        }
    }

    public static PluginManager getPluginManager(INIConfiguration config) {
        String filePluginLocation = getFilePluginLocation(config);
        // create plugin directory if it does not exist
        Path path = Paths.get(filePluginLocation);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
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

    /**
     * Gets the plugins json file path from the config file, otherwise defaults.
     *
     * @param config The parsed config file
     * @return The plugins json file path
     */
    private static String getPluginJSONLocation(INIConfiguration config) {
        String userHome = System.getProperty("user.home");
        String pluginJSONPath = userHome + File.separator + ".dockstore" + File.separator + PLUGINS_JSON_FILENAME;
        return config.getString("plugins-json-location", pluginJSONPath);
    }

    /**
     * Downloads all plugins
     *
     * @param configFile The parsed config file
     */
    public static void downloadPlugins(INIConfiguration configFile) {
        String filePluginLocation = FileProvisionUtil.getFilePluginLocation(configFile);
        String pluginJSONPath = FileProvisionUtil.getPluginJSONLocation(configFile);
        File f = new File(pluginJSONPath);
        if (!f.exists()) {
            if (f.isDirectory()) {
                LOG.error(PLUGINS_JSON_FILENAME + " is actually a directory.");
                System.exit(1);
            } else {
                createPluginJSONFile(pluginJSONPath);
            }
        }
        Gson gson = new Gson();
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(pluginJSONPath), Charset.forName("UTF-8")));
            PluginJSON[] arrayJSON = gson.fromJson(reader, PluginJSON[].class);
            List<PluginJSON> listJSON = Arrays.asList(arrayJSON);
            listJSON.forEach(t -> downloadPlugin(filePluginLocation, t));
        } catch (FileNotFoundException e) {
            LOG.error(PLUGINS_JSON_FILENAME + " not found");
        }
    }

    /**
     * Downloads a plugin
     *
     * @param filePluginLocation The path of the plugins folder
     * @param version            The version of the plugin
     * @param pluginName         The name of the plugin
     * @param sourceLocation     The place to download the plugin from
     */
    private static boolean downloadPlugin(String filePluginLocation, String version, String pluginName, String sourceLocation) {
        String pluginZip = String.format("%2$s-%1$s.zip", version, pluginName);
        Path pluginPath = Paths.get(filePluginLocation, pluginZip);
        String destinationLocation = pluginPath.toString();
        if (Files.exists(pluginPath)) {
            System.out.println("Skipping " + destinationLocation + ", already exists");
            return false;
        } else {
            System.out.println("Downloading " + sourceLocation + " to " + destinationLocation);
            final int pluginDownloadAttempts = 1;
            FileProvisioning.retryWrapper(null, sourceLocation, Paths.get(destinationLocation), pluginDownloadAttempts, true, 1);
            return true;
        }
    }

    /**
     * Extracts plugin information from json and then downloads the plugin
     *
     * @param filePluginLocation The location of the plugins folder
     * @param json               The PluginJSON object
     */
    private static boolean downloadPlugin(String filePluginLocation, PluginJSON json) {
        try {
            LOG.info("Downloading Plugins");
            String version = json.getVersion();
            String name = json.getName();
            String sourceLocation;
            // A location parameter in the json object indicates it's not on oicr artifactory
            if (json.getLocation() == null) {
                String template = "https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/%2$s/%1$s/%2$s-%1$s.zip";
                URL sourceURL = new URI(String.format(template, version, name)).toURL();
                sourceLocation = sourceURL.toString();
            } else {
                sourceLocation = json.getLocation();
            }
            downloadPlugin(filePluginLocation, version, name, sourceLocation);
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            LOG.error("Could not download plugin: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates the plugins json file
     *
     * @param location Location of where to create the file
     */
    static boolean createPluginJSONFile(String location) {
        InputStream in = FileProvisionUtil.class.getResourceAsStream("/" + PLUGINS_JSON_FILENAME);
        File targetFile = new File(location);
        try {
            FileUtils.copyInputStreamToFile(in, targetFile);
            return true;
        } catch (IOException e) {
            LOG.error(e.getMessage() + ". Could not create " + PLUGINS_JSON_FILENAME);
        }
        return false;
    }
}
