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

package io.dockstore.webservice.helpers;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;

/**
 * @author gluu
 * @since 2019-09-11
 */
public final class VerificationHelper {
    private VerificationHelper() {

    }

    public static Set<String> getVerifiedSources(SortedSet<SourceFile> versionSourceFiles) {
        Set<String> verifiedSources = new TreeSet<>();
        versionSourceFiles.forEach(sourceFile -> {
            Map<String, SourceFile.VerificationInformation> verifiedBySource = sourceFile.getVerifiedBySource();
            for (Map.Entry<String, SourceFile.VerificationInformation> thing : verifiedBySource.entrySet()) {
                if (thing.getValue().verified) {
                    verifiedSources.add(thing.getValue().metadata);
                }
            }
        });
        return verifiedSources;
    }

    public static Set<String> getVerifiedSources(Set<? extends Version> workflowVersions) {
        Set<String> verifiedSources = new TreeSet<>();
        workflowVersions.forEach(workflowVersion -> {
            SortedSet<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
            sourceFiles.forEach(sourceFile -> {
                Map<String, SourceFile.VerificationInformation> verifiedBySource = sourceFile.getVerifiedBySource();
                for (Map.Entry<String, SourceFile.VerificationInformation> thing : verifiedBySource.entrySet()) {
                    if (thing.getValue().verified) {
                        verifiedSources.add(thing.getValue().metadata);
                    }
                }
            });
        });
        return verifiedSources;
    }
}
