/*
 *
 *  *    Copyright 2019 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.webservice.core;

/**
 * This class is used as the response object of the entry collections endpoint.
 * @author gluu
 * @since 1.6.0
 */
public class CollectionOrganization {
    public final long collectionId;
    public final String collectionName;
    public final String collectionDisplayName;
    public final long organizationId;
    public final String organizationName;
    public final String organizationDisplayName;

    public CollectionOrganization(long collectionId, String collectionName, String collectionDisplayName, long organizationId, String organizationName, String organizationDisplayName)  {
        this.collectionId = collectionId;
        this.collectionName = collectionName;
        this.collectionDisplayName = collectionDisplayName;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.organizationDisplayName = organizationDisplayName;
    }
}
