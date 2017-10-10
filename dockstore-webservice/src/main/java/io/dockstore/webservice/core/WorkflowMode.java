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
 * This enumerates the modes of the workflows that are currently known to Dockstore.
 *
 * @author dyuen
 */
@ApiModel(description = "This enumerates the modes of the workflows that are currently known to Dockstore.")
public enum WorkflowMode {
    /**
     * A full workflow entry means that a user has attempted to publish this workflow. We should look at all branches
     * and tags for workflows
     */
    FULL, /**
     * A stub workflow entry means that we're aware of a repo. However, we should not do a full refresh or scan into that repo
     * to conserve quota
     */
    STUB
}
