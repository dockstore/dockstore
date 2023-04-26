/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.common;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * All copied from dropwizard 4 due to deletion in dropwizard 5.
 *
 * <a href="https://github.com/dropwizard/dropwizard/blob/9f736f6732373d559626f7dd5bb0a66b5bd94493/dropwizard-testing/src/main/java/io/dropwizard/testing/FixtureHelpers.java">...</a>
 * <a href="https://github.com/dropwizard/dropwizard/blob/9f736f6732373d559626f7dd5bb0a66b5bd94493/dropwizard-util/src/main/java/io/dropwizard/util/Resources.java">...</a>
 */
public final class FixtureUtility {
    private FixtureUtility() {
        //  block construction
    }

    /**
     * Reads the given fixture file from the classpath (e. g. {@code src/test/resources})
     * and returns its contents as a UTF-8 string.
     *
     * @param filename the filename of the fixture file
     * @return the contents of {@code src/test/resources/{filename}}
     * @throws IllegalArgumentException if an I/O error occurs.
     */
    public static String fixture(String filename) {
        return fixture(filename, StandardCharsets.UTF_8);
    }

    /**
     * Reads the given fixture file from the classpath (e. g. {@code src/test/resources})
     * and returns its contents as a string.
     *
     * @param filename the filename of the fixture file
     * @param charset  the character set of {@code filename}
     * @return the contents of {@code src/test/resources/{filename}}
     * @throws IllegalArgumentException if an I/O error occurs.
     */
    private static String fixture(String filename, Charset charset) {
        final URL resource = getResource(filename);
        try (InputStream inputStream = resource.openStream()) {
            return new String(ByteStreams.toByteArray(inputStream), charset).trim();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a {@code URL} pointing to {@code resourceName} if the resource is found using the
     * {@linkplain Thread#getContextClassLoader() context class loader}. In simple environments, the
     * context class loader will find resources from the class path. In environments where different
     * threads can have different class loaders, for example app servers, the context class loader
     * will typically have been set to an appropriate loader for the current thread.
     *
     * <p>In the unusual case where the context class loader is null, the class loader that loaded
     * this class ({@code Resources}) will be used instead.
     *
     * @param resourceName the name of the resource
     * @return the {@link URL} of the resource
     * @throws IllegalArgumentException if the resource is not found
     */
    public static URL getResource(String resourceName) {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader loader = contextClassLoader == null ? FixtureUtility.class.getClassLoader() : contextClassLoader;
        final URL url = loader.getResource(resourceName);
        if (url == null) {
            throw new IllegalArgumentException("resource " + resourceName + " not found.");
        }
        return url;
    }

}
