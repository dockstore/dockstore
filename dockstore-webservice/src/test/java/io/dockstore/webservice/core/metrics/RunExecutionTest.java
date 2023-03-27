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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunExecutionTest {

    @Test
    void testCheckExecutionTimeISO8601Format() {
        assertTrue(RunExecution.checkExecutionTimeISO8601Format("PT5M").isPresent());
        assertTrue(RunExecution.checkExecutionTimeISO8601Format("pt5m").isPresent());
        assertTrue(RunExecution.checkExecutionTimeISO8601Format("PT5M30S").isPresent());
        assertTrue(RunExecution.checkExecutionTimeISO8601Format("PT90S").isPresent());

        assertTrue(RunExecution.checkExecutionTimeISO8601Format("5 seconds").isEmpty());
        assertTrue(RunExecution.checkExecutionTimeISO8601Format("PT 5M").isEmpty());
    }
}
