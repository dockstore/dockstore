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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.zafarkhaja.semver.UnexpectedCharacterException;
import io.dockstore.provision.ProvisionInterface;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

/**
 * The purpose of this class is to provide general functions to deal with workflow file provisioning.
 * Created by aduncan on 10/03/16.
 */
public class FileProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(FileProvisioning.class);

    private List<ProvisionInterface> plugins = new ArrayList<>();

    private INIConfiguration config;

    /**
     * Constructor
     */
    public FileProvisioning(String configFile) {
        this.config = Utilities.parseConfig(configFile);
        try {
            PluginManager pluginManager = FileProvisionUtil.getPluginManager(config);

            this.plugins = pluginManager.getExtensions(ProvisionInterface.class);

            List<PluginWrapper> pluginWrappers = pluginManager.getPlugins();
            for (PluginWrapper pluginWrapper : pluginWrappers) {
                SubnodeConfiguration section = config.getSection(pluginWrapper.getPluginId());
                Map<String, String> sectionConfig = new HashMap<>();
                Iterator<String> keys = section.getKeys();
                keys.forEachRemaining(key -> sectionConfig.put(key, section.getString(key)));
                // this is ugly, but we need to pass configuration into the plugins
                // TODO: speed this up using a map of plugins
                for (ProvisionInterface extension : plugins) {
                    String extensionName = extension.getClass().getName();
                    String pluginClass = pluginWrapper.getDescriptor().getPluginClass();
                    if (extensionName.startsWith(pluginClass)) {
                        extension.setConfiguration(sectionConfig);
                    }
                }
            }
        } catch (UnexpectedCharacterException e) {
            LOG.error("Could not load plugins: " + e.toString(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * This method downloads both local and remote files into the working directory
     *
     * @param targetPath path for target file
     * @param localPath  the absolute path where we will download files to
     */
    public void provisionInputFile(String targetPath, Path localPath) {

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

        URI objectIdentifier = URI.create(targetPath);    // throws IllegalArgumentException if it isn't a valid URI
        if (objectIdentifier.getScheme() != null) {
            String scheme = objectIdentifier.getScheme().toLowerCase();
            for (ProvisionInterface provision : plugins) {
                if (provision.schemesHandled().contains(scheme.toUpperCase()) || provision.schemesHandled().contains(scheme.toLowerCase())) {
                    boolean downloaded = provision.downloadFrom(targetPath, localPath);
                    if (!downloaded) {
                        throw new RuntimeException("Could not provision: " + targetPath + " to " + localPath);
                    }
                }
            }
        }
        // if a file does not exist yet, get it
        if (!Files.exists(localPath)) {
            // check if we can use a plugin
            boolean localFileType = objectIdentifier.getScheme() == null;

            if (!localFileType) {
                FileProvisionUtil.downloadFromVFS2(targetPath, localPath.toFile().getAbsolutePath());
            } else {
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
        URI objectIdentifier = URI.create(destPath);    // throws IllegalArgumentException if it isn't a valid URI
        File sourceFile = new File(srcPath);
        long inputSize = sourceFile.length();

        if (objectIdentifier.getScheme() != null) {
            String scheme = objectIdentifier.getScheme();
            for (ProvisionInterface provision : plugins) {
                if (provision.schemesHandled().contains(scheme.toUpperCase()) || provision.schemesHandled().contains(scheme.toLowerCase())) {
                    boolean uploaded = provision.uploadTo(destPath, Paths.get(srcPath), null);
                    if (!uploaded) {
                        throw new RuntimeException("Could not provision: " + srcPath + " to " + destPath);
                    }
                    return;
                }
            }
        }
        try {
            FileSystemManager fsManager;
            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
            fsManager = VFS.getManager();
            // check for a local file path
            Path currentWorkingDir = Paths.get("").toAbsolutePath();
            FileObject dest = fsManager.resolveFile(currentWorkingDir.toFile(), destPath);
            FileObject src = fsManager.resolveFile(sourceFile.getAbsolutePath());
            FileProvisionUtil.copyFromInputStreamToOutputStream(src.getContent().getInputStream(), inputSize, dest.getContent().getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not provision output files", e);
        }
    }

    public static void main(String[] args) {
        String userHome = System.getProperty("user.home");
        String pluginPath = userHome + File.separator + ".dockstore" + File.separator + "plugins";

        PluginManager pluginManager = new DefaultPluginManager(new File(pluginPath));
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<ProvisionInterface> greetings = pluginManager.getExtensions(ProvisionInterface.class);
        for (ProvisionInterface provision : greetings) {
            System.out.println("Plugin: " + provision.getClass().getName());
            System.out.println("\tSchemes handled: " + provision.getClass().getName());
            for (String prefix: provision.schemesHandled()) {
                System.out.println("\t\t " + prefix);
            }
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

