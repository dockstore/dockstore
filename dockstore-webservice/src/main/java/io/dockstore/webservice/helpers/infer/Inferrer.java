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

package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import java.nio.file.Path;
import java.util.List;

/**
 * Infers the details of entries present in the specified file tree.
 * Typically, an implementation of Inferrer will infer entries for a particular
 * descriptor language (CWL, WDL, etc) or interpret a particular type of manifest file.
 * An Inferrer might use various heuristics to make an educated guess about whether a
 * particular file represents an entry (or not).
 */
public interface Inferrer {

    /**
     * Infers the details of entries present in the specified file tree.
     * @return a list of inferred entries
     */
    List<Entry> infer(FileTree fileTree);

    /**
     * Returns true if the file tree has a descriptor path. Used to quickly determine if the file tree potentially has entries
     * without inferring the whole file tree.
     * @param fileTree
     * @return
     */
    boolean containsDescriptorPath(FileTree fileTree);

    /**
     * Describes an inferred entry.
     */
    record Entry(EntryType type, DescriptorLanguage language, DescriptorLanguageSubclass subclass, Path path, String name) {
        public Entry changeName(String newName) {
            return new Entry(type, language, subclass, path, newName);
        }
    }
}
