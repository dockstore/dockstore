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

import io.dockstore.common.DescriptorLanguage;

public final class LanguageHandlerFactory {
    private LanguageHandlerFactory() {
        // do nothing constructor
    }

    public static LanguageHandlerInterface getInterface(DescriptorLanguage type) {
        switch (type) {
        case CWL:
            return new CWLHandler();
        case WDL:
            return new WDLHandler();
        case NEXTFLOW:
            return new NextflowHandler();
        // DOCKSTORE-2428 - demo how to add new workflow language
        //        case SWL:
        //            return new LanguagePluginHandler(SillyWorkflowLanguagePlugin.class);
        case SERVICE:
            return new LanguagePluginHandler(ServicePrototypePlugin.class);
        default:
            throw new UnsupportedOperationException("language not known");
        }
    }

    public static LanguageHandlerInterface getInterface(DescriptorLanguage.FileType type) {
        switch (type) {
        case DOCKSTORE_CWL:
            return new CWLHandler();
        case DOCKSTORE_WDL:
            return new WDLHandler();
        case NEXTFLOW_CONFIG:
            return new NextflowHandler();
        // DOCKSTORE-2428 - demo how to add new workflow language
        //        case DOCKSTORE_SWL:
        //            return new LanguagePluginHandler(SillyWorkflowLanguagePlugin.class);
        case DOCKSTORE_SERVICE_YML:
            return new LanguagePluginHandler(ServicePrototypePlugin.class);
        default:
            throw new UnsupportedOperationException("language not known");
        }
    }
}
