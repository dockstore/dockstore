/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidationExecutionTest {

    @Test
    void testCheckExecutionDateISO8601Format() {
        assertTrue(ValidationExecution.checkExecutionDateISO8601Format("2023-03-31T15:06:49.888745366Z").isPresent());
        assertTrue(ValidationExecution.checkExecutionDateISO8601Format("2023-03-31T15:06:49Z").isPresent());
        assertFalse(ValidationExecution.checkExecutionDateISO8601Format("2023-03-31T15:06:49").isPresent(), "Should fail because it's not in UTC");
    }
}
