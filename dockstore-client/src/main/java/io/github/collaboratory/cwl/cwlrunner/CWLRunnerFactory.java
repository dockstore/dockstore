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
package io.github.collaboratory.cwl.cwlrunner;

import org.apache.commons.configuration2.INIConfiguration;

public final class CWLRunnerFactory {

    public enum CWLRunner { BUNNY, CWLTOOL, TOIL }

    private static INIConfiguration config = null;

    private CWLRunnerFactory() {
        // suppress constructor
    }

    public static CWLRunnerInterface createCWLRunner() {
        if (config == null) {
            throw new UnsupportedOperationException("configuration is not setup");
        }
        String string = getCWLRunner();
        if (CWLRunner.CWLTOOL.toString().equalsIgnoreCase(string)) {
            return new CWLToolWrapper();
        } else if (CWLRunner.BUNNY.toString().equalsIgnoreCase(string)) {
            return new BunnyWrapper(config);
        } else if (CWLRunner.TOIL.toString().equalsIgnoreCase(string)) {
            return new ToilWrapper();
        } else if ("cwl-runner".equalsIgnoreCase(string)) {
            return new GenericCWLRunnerWrapper();
        } else {
            throw new UnsupportedOperationException("Improper CWL-runner specified");
        }
    }

    public static void setConfig(INIConfiguration config) {
        CWLRunnerFactory.config = config;
    }

    public static String getCWLRunner() {
        return config.getString("cwlrunner", CWLRunner.CWLTOOL.toString());
    }
}
