/*
 *    Copyright 2019 OICR
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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Currently stores alias information for Collections, Organizations, Tools and Workflows.
 * For now its just blank, but can be expanded with additional information on the aliases (such as if they
 * point at dockstore mirrors)
 */
@Embeddable
public class Alias implements Serializable {

    @Column(columnDefinition = "text")
    public String content = "";

    // database timestamps
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Timestamp dbCreateDate;

    // There is no dbupdatedate because it doesn't work with @Embeddable nor @ElementCollection

}
