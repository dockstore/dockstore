/*
 *    Copyright 2018 OICR
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
package io.swagger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dockstore.webservice.core.SourceFile;
import java.util.Objects;

/**
 * Used to store additional transient information about files to be returned from the GA4GH endpoints
 */
public class ExtendedFileWrapper extends FileWrapper  {

    @JsonIgnore
    private SourceFile originalFile = null;

    public SourceFile getOriginalFile() {
        return originalFile;
    }

    public void setOriginalFile(SourceFile originalFile) {
        this.originalFile = originalFile;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), super.getContent(), super.getUrl());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileWrapper fileWrapper)) {
            return false;
        }
        return Objects.equals(super.getContent(), fileWrapper.getContent()) && Objects.equals(super.getUrl(), fileWrapper.getUrl());
    }
}
