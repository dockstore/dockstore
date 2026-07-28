/*
 *    Copyright 2021 OICR
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
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stores PKCE information. Intentionally has no swagger or openapi annotations, should not be used by any client directly.
 */
@Entity
@Table(name = "PKCE")
public class PKCE implements Serializable {

    @Id
    private String state;

    @Column(columnDefinition = "varchar(255)", nullable = false)
    private String verifier;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column(updatable = false)
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public PKCE() {

    }

    public PKCE(String state, String verifier) {
        this.state = state;
        this.verifier = verifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PKCE that)) {
            return false;
        }

        return Objects.equals(getState(), that.getState()) && Objects.equals(getVerifier(), that.getVerifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getState(), getVerifier());
    }

    public String getState() {
        return state;
    }

    public String getVerifier() {
        return verifier;
    }
}
