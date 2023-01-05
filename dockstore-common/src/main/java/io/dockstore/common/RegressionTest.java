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

/**
 * Tests with an older dockstore client with the current webservice
 * Check CommonTestUtilities.OLD_DOCKSTORE_VERSION to see which old dockstore client is being tested
 * Testing Dockstore CLI 1.3.6 at the time of creation
 * @author gluu
 * @since 1.4.0
 */
public interface RegressionTest {
    String NAME = "io.dockstore.common.RegressionTest";

    default String getName() {
        // silly method to satisfy CheckStyle
        return NAME;
    }
}
