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

package io.dockstore.webservice.core.tooltester;

/**
 * @author gluu
 * @since 24/04/19
 */
public class ToolTesterLog {
    private String toolId;
    private String toolVersionName;
    private String testFilename;
    private String runner;
    private ToolTesterLogType logType;
    private String filename;

    public ToolTesterLog(String toolId, String toolVersionName, String testFilename, String runner, ToolTesterLogType logType, String filename) {
        this.toolId = toolId;
        this.toolVersionName = toolVersionName;
        this.testFilename = testFilename;
        this.runner = runner;
        this.logType = logType;
        this.filename = filename;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getToolVersionName() {
        return toolVersionName;
    }

    public void setToolVersionName(String toolVersionName) {
        this.toolVersionName = toolVersionName;
    }

    public String getTestFilename() {
        return testFilename;
    }

    public void setTestFilename(String testFilename) {
        this.testFilename = testFilename;
    }

    public String getRunner() {
        return runner;
    }

    public void setRunner(String runner) {
        this.runner = runner;
    }

    public ToolTesterLogType getLogType() {
        return logType;
    }

    public void setLogType(ToolTesterLogType logType) {
        this.logType = logType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
