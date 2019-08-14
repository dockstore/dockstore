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
package io.dockstore.client.cli;

import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.client.cli.Client.DEPRECATED_PORT_MESSAGE;

/**
 * Created by dyuen on 2/23/17.
 */
public class ClientTestIT {

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Test
    public void testDependencies(){
        String config = ResourceHelpers.resourceFilePath("config");
        CWLRunnerFactory.setConfig(Utilities.parseConfig(config));
        Assert.assertTrue(!systemErrRule.getLog().contains("Override and run with"));
        Assert.assertTrue(!systemOutRule.getLog().contains("Override and run with"));
    }

    /**
     * When javax.activation is missing (because using Java 11), there will be error logs
     * Check that there are no error logs
     * Careful, test scope may interfere with validity of this test
     */
    @Test
    public void noErrorLogs() {
        String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        String[] command = { "--help", "--config", clientConfig };
        Client.main(command);
        Assert.assertTrue("There are unexpected error logs", systemErrRule.getLog().isBlank());
        Assert.assertFalse("Should not have warned about port 8443", systemErrRule.getLog().contains(DEPRECATED_PORT_MESSAGE));
    }

    /**
     * When the old 8443 port is used, the user should be warned
     */
    @Test
    public void testPort8443() {
        String clientConfig = ResourceHelpers.resourceFilePath("oldClientConfig");
        String[] command = { "--help", "--config", clientConfig };
        Client.main(command);
        Assert.assertTrue("Should have warned about port 8443", systemErrRule.getLog().contains(DEPRECATED_PORT_MESSAGE));
    }
}
