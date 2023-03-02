/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.resources;

/**
 * STARRED_ENTRIES return events related to starred entries (i.e. what has the user starred and finds interesting)
 * STARRED_ORGANIZATION return events related to starred organization (i.e. what organizations has the user starred and finds interesting?)
 * STARRED returns events related to both starred entries AND starred organizations
 * PROFILE returns events about the user that others would be interested in (i.e. what has the user done that is interesting to others?)
 * SELF_ORGANIZATIONS returns events that occurred in organizations that the user is a part of
 */
public enum EventSearchType  {
    STARRED_ENTRIES,
    STARRED_ORGANIZATION,
    ALL_STARRED,
    PROFILE,
    SELF_ORGANIZATIONS
}
