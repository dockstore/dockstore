/*
 *    Copyright 2018 OICR
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
package io.dockstore.webservice.resources;

import io.swagger.v3.jaxrs2.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo how to debug swagger parsing of our classes
 */
public final class SwaggerTester {

    private static final Logger LOG = LoggerFactory.getLogger(SwaggerTester.class);


    private SwaggerTester() {
        // hide utility class
    }

    public static void main(String[] args) {
        Reader reader = new Reader();
        reader.read(AbstractHostedEntryResource.class);
    }
}
