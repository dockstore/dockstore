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

package io.dockstore.common;

import cloud.localstack.docker.annotation.IEnvironmentVariableProvider;
import java.util.Map;

public final class LocalStackTestUtilities {
    public static final String IMAGE_TAG = "1.3.1";
    public static final String ENDPOINT_OVERRIDE = "http://localhost:4566";
    public static final String AWS_REGION_ENV_VAR = "AWS_REGION";

    private LocalStackTestUtilities() {}

    public static class LocalStackEnvironmentVariables implements IEnvironmentVariableProvider {
        @Override
        public Map<String, String> getEnvironmentVariables() {
            // Need this so that S3 key encoding works. Remove when there's a new localstack release containing the fix
            // https://github.com/localstack/localstack/issues/7374#issuecomment-1360950643
            return Map.of("PROVIDER_OVERRIDE_S3", "asf");
        }
    }
}
