/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.dockstore.webservice.helpers.doi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import org.apache.http.HttpStatus;

public class EzidDoiRegistrar implements DoiRegistrar {

    private final String user;
    private final String password;

    public EzidDoiRegistrar(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public String createDoi(String name, String url, String metadata) {
        try {
            String request = anvl("_target", url) + anvl("datacite", metadata);
            URL ezid = new URL("https://ezid.cdlib.org/id/doi:" + name);
            if (ezid.openConnection() instanceof HttpsURLConnection c) {
                c.setRequestMethod("PUT");
                c.setRequestProperty("Content-Type", "text/plain");
                c.setRequestProperty("Accept", "text/plain");
                c.setRequestProperty("Authorization", "Basic " + toBase64(user + ":" + password));
                c.setDoInput(true);
                c.setDoOutput(true);
                c.connect();
                try (OutputStream out = c.getOutputStream()) {
                    out.write(toBytes(request));
                }
                String response;
                try (InputStream in = c.getInputStream()) {
                    response = toString(in.readAllBytes());
                }
                c.disconnect();
                if (c.getResponseCode() == HttpStatus.SC_CREATED) {
                    return name;
                } else {
                    throw new RuntimeException("Could not create DOI: " + response);
                }
            } else {
                throw new RuntimeException("Not an HTTPS connection");
            }
        } catch (IOException e) {
            throw new RuntimeException("IO exception", e);
        }
    }

    private static String anvl(String key, String value) {
        return "%s: %s\n".formatted(encode(key), encode(value));
    }

    private static String encode(String s) {
        return s.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D").replace(":", "%3A");
    }

    private static String toBase64(String s) {
        return toString(Base64.getEncoder().encode(toBytes(s)));
    }

    private static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

