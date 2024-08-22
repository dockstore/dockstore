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

package io.dockstore.webservice.core.webhook;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Information about a commit on a GitHub push event. See https://docs.github.com/en/webhooks/webhook-events-and-payloads#push, under commits and head_commit")
public class GitCommit {

    private GitHubUser author;
    private GitHubUser committer;

    public GitHubUser getAuthor() {
        return author;
    }

    public void setAuthor(GitHubUser author) {
        this.author = author;
    }

    public GitHubUser getCommitter() {
        return committer;
    }

    public void setCommitter(GitHubUser committer) {
        this.committer = committer;
    }

    public record GitHubUser(String username, String name, String email) {}
}

