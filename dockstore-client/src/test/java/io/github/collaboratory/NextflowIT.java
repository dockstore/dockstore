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
package io.github.collaboratory;

import java.util.List;

import com.google.common.base.Joiner;
import io.github.collaboratory.nextflow.NextFlowFacade;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Test;

public class NextflowIT {
    @Test
    public void demoNextFlowLaunch() {
        String nextflowFile = FileUtils.getFile("src", "test", "resources", "nextflow_rnatoy", "main.nf").getAbsolutePath();
        String nextflowParamsFile = FileUtils.getFile("src", "test", "resources", "nextflow_rnatoy", "test.json").getAbsolutePath();


        String absolutePath = FileUtils.getFile("src", "test", "resources", "launcher.nextflow.ini").getAbsolutePath();
        INIConfiguration iniConfiguration = Utilities.parseConfig(absolutePath);
        NextFlowFacade nextFlowFacade = new NextFlowFacade(iniConfiguration);
        List<String> executionCommand = nextFlowFacade
            .getExecutionCommand("./datastore/outdir", "./datastore/test", nextflowFile, nextflowParamsFile);
        final String join = Joiner.on(" ").join(executionCommand);
        ImmutablePair<String, String> stringStringImmutablePair = Utilities.executeCommand(join);
        Assert.assertTrue("could not find completion message", stringStringImmutablePair.left.contains("results world!"));
    }
}
