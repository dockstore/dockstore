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

package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;

/**
 * This enumerates the types of containers (really, images) that we can add to the dockstore. This will become more prominent later if we
 * proceed with privacy and http link support.
 *
 * The convention here seems to be that auto-detected tools can become mixed mode tools. However, neither of these two can become manual or vice versa.
 * Exception: If a user wants to add two tools based on the same Docker image but with different descriptors, then we currently have them add a new tool with the same
 * namespace and repo, but add a toolname to distinguish the two. These can get converted to auto-detected builds.
 *
 * @author dyuen
 */
@ApiModel(description = "This enumerates the types of containers (really, images) that we can add to the dockstore. Implementation specific.")
public enum ToolMode {
    /**
     * from quay.io automated builds or not, try to track back to source control regardless of whether it is github or bitbucket and find
     * Dockerfiles and dockstore.cwl for all tags, track back to git identifier via quay.io API, find documents in default location
     * specified by wizard
     */
    AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS, /**
     * from quay.io automated builds or not, try to track back to source control regardless of whether it is github or bitbucket and find
     * Dockerfiles and dockstore.cwl if automated, track back to git identifier via quay.io API, find documents in default location
     * specified by wizard if not automated, cannot track back, skip until specified, find documents in default location specified by wizard
     */
    AUTO_DETECT_QUAY_TAGS_WITH_MIXED, /**
     * from quay.io or Docker Hub, the user simply enters an image path (ex: org/foobar or quay.io/org/foobar) and then picks a source repo
     * and then enters most remaining info (source tag, image tag, paths)
     */
    MANUAL_IMAGE_PATH
}
