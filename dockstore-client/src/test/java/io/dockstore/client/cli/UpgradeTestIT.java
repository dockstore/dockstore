/*
 *    Copyright 2016 OICR
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.io.Resources;
import io.dockstore.common.TestUtility;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpgradeTestIT {
    private ObjectMapper objectMapper;

    @Before
    public void setup() throws IOException{
        /* One problem with using any(URL.class): because it's catching "any(URL.class)",
           the current version will read the latest-release.json.
         * It can be changed by changing the 'URL.class' to be a specific URL needed to be tested,
           also need to change the file according to the current version
         * For testing purposes of upgrading right away from OLD unstable/stable version by inputting
           the command '--upgrade', change the variable of the 'currentVersion' in Client.java to be
           '0.3-beta.1' for stable or '0.3-beta.0' for unstable */

        this.objectMapper = mock(ObjectMapper.class);
        Client.setObjectMapper(objectMapper);

        ObjectMapper localObjectMapper = new ObjectMapper();
        Map map = localObjectMapper.readValue(Resources.getResource("latest-release.json"), Map.class);
        Map map1 = localObjectMapper.readValue(Resources.getResource("current-version.json"), Map.class);
        Map map2 = localObjectMapper.readValue(Resources.getResource("current-stable.json"),Map.class);
        Map map3 = localObjectMapper.readValue(Resources.getResource("stable-old.json"),Map.class);
        Map map4 = localObjectMapper.readValue(Resources.getResource("unstable-old.json"),Map.class);
        Map map5 = localObjectMapper.readValue(Resources.getResource("dummy-unstable.json"),Map.class);
        URL latest = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/latest");
        URL current = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/0.4-beta.0");
        URL curstable = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/0.3-beta.1");
        URL stableOld = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/0.3-beta.0");
        URL unstableOld = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/0.3-alpha.0");
        URL unstableDummy = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/0.4-beta.1-SNAPSHOT");
        URL all = new URL("https://api.github.com/repos/ga4gh/dockstore/releases");

        ArrayList list = localObjectMapper.readValue(Resources.getResource("all-releases.json"), ArrayList.class);
        when(objectMapper.readValue(eq(latest),eq(Map.class))).thenReturn(map);
        when(objectMapper.readValue(eq(current),eq(Map.class))).thenReturn(map1);
        when(objectMapper.readValue(eq(curstable),eq(Map.class))).thenReturn(map2);
        when(objectMapper.readValue(eq(stableOld),eq(Map.class))).thenReturn(map3);
        when(objectMapper.readValue(eq(unstableOld),eq(Map.class))).thenReturn(map4);
        when(objectMapper.readValue(eq(unstableDummy),eq(Map.class))).thenReturn(map5);
        when(objectMapper.readValue(eq(all),eq(ArrayList.class))).thenReturn(list);
        when(objectMapper.getTypeFactory()).thenReturn(localObjectMapper.getTypeFactory());

        TypeFactory typeFactory = localObjectMapper.getTypeFactory();
        CollectionType ct = typeFactory.constructCollectionType(List.class, Map.class);
        Object mapRel = localObjectMapper.readValue(Resources.getResource("all-releases.json"), ct);
        when(objectMapper.readValue(eq(all),any(CollectionType.class))).thenReturn(mapRel);
    }

    @Test
    public void upgradeUnstable() throws IOException {
        //if the current is stable, upgrade to the most unstable version
        //else, output "you are currently running on the latest unstable version" and option to "--upgrade-stable"
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--debug", "--upgrade-unstable"});
    }

    @Test
    public void upgradeStable() throws IOException {
        //if the current is unstable, upgrade to the latest stable version
        //else, output "you are currently running the most stable version" and option to "--upgrade-unstable"
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--debug", "--upgrade-stable"});
    }

    @Test
    public void upgradeTest() throws IOException {
        //if the current is newer and unstable, output "--upgrade-stable" command option
        //else if current is the latest stable version, output "you are currently running the most stable version"
        //         and option to "--upgrade-unstable"
        //else(current is older), upgrade to the most stable version right away
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--debug", "--upgrade"});
    }

}

