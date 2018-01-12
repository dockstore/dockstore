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
package io.dockstore.webservice.languages;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.webservice.core.SourceFile;

public final class LanguageHandlerFactory {
    private LanguageHandlerFactory() {
        // do nothing constructor
    }

    public static boolean isWorkflow(SourceFile.FileType identifiedType) {
        return identifiedType == SourceFile.FileType.DOCKSTORE_CWL || identifiedType == SourceFile.FileType.DOCKSTORE_WDL
            || identifiedType == SourceFile.FileType.NEXTFLOW_CONFIG;
    }

    public static LanguageHandlerInterface getInterface(AbstractEntryClient.Type type) {
        switch (type) {
        case CWL:
            return new CWLHandler();
        case WDL:
            return new WDLHandler();
        case NEXTFLOW:
            return new NextFlowHandler();
        default:
            throw new UnsupportedOperationException("language not known");
        }
    }

    public static LanguageHandlerInterface getInterface(SourceFile.FileType type) {
        switch (type) {
        case DOCKSTORE_CWL:
            return new CWLHandler();
        case DOCKSTORE_WDL:
            return new WDLHandler();
        case NEXTFLOW_CONFIG:
            return new NextFlowHandler();
        default:
            throw new UnsupportedOperationException("language not known");
        }
    }
}
