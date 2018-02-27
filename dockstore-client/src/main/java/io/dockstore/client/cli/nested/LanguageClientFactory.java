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
package io.dockstore.client.cli.nested;

public final class LanguageClientFactory {

    private LanguageClientFactory() {
        // suppress constructor
    }

    public static LanguageClientInterface createLanguageCLient(AbstractEntryClient client, AbstractEntryClient.Type type) {
        if (type == AbstractEntryClient.Type.CWL) {
            return new CWLClient(client);
        } else if (type == AbstractEntryClient.Type.WDL) {
            return new WDLClient(client);
        } else if (type == AbstractEntryClient.Type.NEXTFLOW) {
            return new WDLClient(client);
        } else {
            throw new UnsupportedOperationException("language client does not exist for " + type);
        }
    }
}
