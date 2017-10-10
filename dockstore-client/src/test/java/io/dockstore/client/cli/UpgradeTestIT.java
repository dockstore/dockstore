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

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class UpgradeTestIT {
    private ObjectMapper objectMapper;

    /**
     * This method will decide which version to upgrade to depending on the command "stable","unstable", and "none"
     * for testing 'UpgradeTestIT'
     *
     * @param upCommand
     * @param current
     * @param latestStable
     * @param latestUnstable
     * @return
     */

    public static String decideOutput(String upCommand, String current, String latestStable, String latestUnstable) {
        //upCommand = the command being used (i.e --upgrade-stable or --upgrade or --upgrade-unstable)

        if (current.equals(latestStable)) {   //current is the latest stable version
            if (upCommand.equals("unstable")) {   // downgrade or upgrade to latest unstable version
                return latestUnstable;
            } else {
                return "upgrade-unstable";
            }
        } else {    //current is not the latest stable version
            if ("stable".equals(upCommand)) {
                //upgrade to latest stable
                return latestStable;
            } else if ("none".equals(upCommand)) {
                if (current.equals(latestUnstable)) {
                    //current version is latest unstable version
                    return "upgrade-stable";
                } else {
                    // current version is not the latest unstable version
                    // upgrade to latest stable version
                    return latestStable;
                }
            } else if ("unstable".equals(upCommand)) {
                if (current.equals(latestUnstable)) {
                    // current version is the latest unstable version
                    return "upgrade-stable";
                } else {
                    //user wants to upgrade to latest unstable version
                    //upgrade to latest unstable
                    return latestUnstable;
                }
            }
        }
        return null;
    }

    @Before
    public void setup() throws IOException {

        this.objectMapper = mock(ObjectMapper.class);
        Client.setObjectMapper(objectMapper);


        /**
         URL latest = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/latest");
         URL all = new URL("https://api.github.com/repos/ga4gh/dockstore/releases");

         Map map = localObjectMapper.readValue(latest,Map.class);

         ArrayList list = localObjectMapper.readValue(all, ArrayList.class);
         when(objectMapper.readValue(eq(latest),eq(Map.class))).thenReturn(map);
         when(objectMapper.readValue(eq(all),eq(ArrayList.class))).thenReturn(list);
         when(objectMapper.getTypeFactory()).thenReturn(localObjectMapper.getTypeFactory());

         TypeFactory typeFactory = localObjectMapper.getTypeFactory();
         CollectionType ct = typeFactory.constructCollectionType(List.class, Map.class);
         Object mapRel = localObjectMapper.readValue(all, ct);
         when(objectMapper.readValue(eq(all),any(CollectionType.class))).thenReturn(mapRel);
         */
    }

    @Test
    public void upgradeTest() throws IOException {
        //if current is older, upgrade to the most stable version right away
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.3-beta.1";
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String desiredVersion = decideOutput("none", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(desiredVersion, "0.4-beta.1"));
    }

    @Test
    public void upTestStableOption() throws IOException {
        //if the current is newer and unstable, output "--upgrade-stable" command option
        String detectedVersion = "0.3-beta.1";  //detectedVersion is the latest stable
        String currentVersion = "0.4-beta.0";   //current is newer and unstable
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String optCommand = decideOutput("none", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(optCommand, "upgrade-stable"));
    }

    @Test
    public void upTestUnstableOption() throws IOException {
        //else if current is the latest stable version, output "you are currently running the most stable version"
        //         and option to "--upgrade-unstable"
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.4-beta.1";
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String optCommand = decideOutput("none", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(optCommand, "upgrade-unstable"));
    }

    @Test
    public void upgradeStable() throws IOException {
        //if the current is not latest stable, upgrade to the latest stable version
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.4-beta.0";  //can also be 0.3-beta.1 , as long as it's not latest stable
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String desiredVersion = decideOutput("stable", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(desiredVersion, "0.4-beta.1"));
    }

    @Test
    public void upgradeStableOption() throws IOException {
        //if the current is latest stable, output option to "--upgrade-unstable"
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.4-beta.1";
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String optCommand = decideOutput("stable", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(optCommand, "upgrade-unstable"));
    }

    @Test
    public void upgradeUnstable() throws IOException {
        //if the current is not latest unstable, upgrade to the most unstable version
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.3-beta.0";
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String desiredVersion = decideOutput("unstable", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(desiredVersion, "0.4-beta.0"));
    }

    @Test
    public void upgradeUnstableOption() throws IOException {
        //if the current is latest unstable, output option to "--upgrade-stable"'
        String detectedVersion = "0.4-beta.1";
        String currentVersion = "0.4-beta.0";
        String unstable = "0.4-beta.0";
        // assert that the value matches the mocking
        String optCommand = decideOutput("unstable", currentVersion, detectedVersion, unstable);
        assert (Objects.equals(optCommand, "upgrade-stable"));
    }
}

