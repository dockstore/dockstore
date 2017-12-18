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
 * This enumerates the types of source control that we can associate an entry with.
 *
 * @author agduncan94
 */
public enum SourceControl {
    // Add new source control here
    GITHUB("github.com", "GitHub"), BITBUCKET("bitbucket.org", "BitBucket"), GITLAB("gitlab.com", "GitLab");

    /**
     * this name is used in the source control path
     */
    private final String sourceControlPath;

    /**
     * this name is what is displayed to users to name the source control
     */
    private final String friendlyName;

    SourceControl(final String sourceControlPath, final String friendlyName) {
        this.sourceControlPath = sourceControlPath;
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return sourceControlPath;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * Expanded version for API list of source control
     */
    public static class SourceControlBean {

        public String value;

        public String friendlyName;

        public SourceControlBean(SourceControl sourceControl) {
            this.value = sourceControl.toString();
            this.friendlyName = sourceControl.getFriendlyName();
        }
    }
}
