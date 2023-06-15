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

package io.dockstore.common.metrics;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FormatCheckHelper {
    private FormatCheckHelper() {

    }

    private static final Logger LOG = LoggerFactory.getLogger(FormatCheckHelper.class);

    /**
     * Check that the execution time is in ISO-8601 format by parsing it into a Duration.
     * @param executionTime ISO 8601 execution time
     * @return Duration parsed from the ISO 8601 execution time
     */
    public static Optional<Duration> checkExecutionTimeISO8601Format(String executionTime) {
        try {
            return Optional.of(Duration.parse(executionTime));
        } catch (DateTimeParseException e) {
            LOG.warn("Execution time {} is not in ISO 8601 format and could not be parsed to a Duration", executionTime, e);
            return Optional.empty();
        }
    }

    /**
     * Check that the execution time is in ISO-8601 format by parsing it into a Date.
     * @param executionDate ISO 8601 execution date
     * @return Date parsed from the ISO 8601 execution date
     */
    public static Optional<Date> checkExecutionDateISO8601Format(String executionDate) {
        try {
            return Optional.of(Date.from(Instant.parse(executionDate)));
        } catch (Exception e) {
            LOG.warn("Execution date {} is not in ISO 8601 date format and could not be parsed to a Date", executionDate, e);
            return Optional.empty();
        }
    }
}
