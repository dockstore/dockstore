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

package io.dockstore.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by aduncan on 19/10/16.
 */
public class BridgeHelper {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeHelper.class);

    /**
     * This resolves a URL into file content
     * @param importUrl
     * @return content of file
     */
    public String resolveUrl(String importUrl) {
        String content = "";

        // Check if valid URL
        UrlValidator urlValidator = new UrlValidator();
        if (urlValidator.isValid(importUrl)) {

            // Grab file located at URL
            try {
                InputStream inputStream = new URL(importUrl).openStream();
                try {
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        content += line;
                    }
                } finally {
                    inputStream.close();
                }
            } catch (MalformedURLException ex) {
                LOG.debug("Invalid URL: " + importUrl);
            } catch (IOException ex) {
                LOG.debug("Error parsing contents of " + importUrl);
            }
        } else {
            LOG.debug("Invalid URL: " + importUrl);
        }
        return content;
    }

    public String resolveLocalPath(String importPath, Map<String, String> secondaryFileDesc) {
        String content = "";

        // Check if local path has been imported
        if (secondaryFileDesc.get(importPath) != null) {
            return secondaryFileDesc.get(importPath);
        }
        return content;
    }
}
