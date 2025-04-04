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

package io.dockstore.webservice.helpers;

import org.kohsuke.github.GHRateLimit;

public final class RateLimitHelper {

    private RateLimitHelper() {
        // This space intentionally left blank.
    }

    public static Reporter reporter(GitHubSourceCodeRepo repo, String methodName) {
        return new Reporter(repo, methodName);
    }

    public static Reporter reporter(GitHubSourceCodeRepo repo) {
        return reporter(repo, StackWalker.getInstance().walk(stream -> stream.skip(1).findFirst()).map(StackWalker.StackFrame::getMethodName).orElse("unknown"));
    }

    public static class Reporter implements AutoCloseable {
        private final GitHubSourceCodeRepo repo;
        private final String methodName;
        private final GHRateLimit startRateLimit;

        public Reporter(GitHubSourceCodeRepo repo, String methodName) {
            this.repo = repo;
            this.methodName = methodName;
            startRateLimit = repo.getGhRateLimitQuietly();
        }

        @Override
        public void close() {
            GHRateLimit endRateLimit = repo.getGhRateLimitQuietly();
            repo.reportOnRateLimit(methodName, startRateLimit, endRateLimit);
        }
    }
}

