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

package core;

import com.google.common.collect.Ordering;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Not really a test, this is used to generate a yaml representation of our webservice for future reference.
 * @author dyuen
 */
public class GenerateYamlTest {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));


    @Test
    public void generateYAML() throws IOException {
        final int localPort = RULE.getLocalPort();
        final String swagger_filename = "/swagger.yaml";
        File destination = new File(System.getProperty("baseDir")+"/src/main/resources/", "swagger.yaml");
        final URL url = new URL("http", "localhost", localPort, swagger_filename);
        System.out.println(url.toString());
        FileUtils.copyURLToFile(url,destination);


        // try sorting it
        Yaml yaml = new Yaml(new SorterRepresenter());
        final Object load = yaml.load(FileUtils.readFileToString(destination, StandardCharsets.UTF_8));
        // a re-dump might be sorted
        String strDump = yaml.dump(load);
        FileUtils.write(destination, strDump, StandardCharsets.UTF_8);
    }

    private class SorterRepresenter extends Representer{
        SorterRepresenter() {
            this.representers.put(LinkedHashMap.class, new MapSorter());
        }

        private class MapSorter implements Represent {
            public Node representData(Object data) {
                Map map = (Map) data;
                Object sortedMap = sortMapByKey(map);
                return SorterRepresenter.this.representData(sortedMap);
            }

            private Object sortMapByKey(Object object) {
                if (object instanceof Map) {
                    TreeMap sortedMap = new TreeMap(Ordering.natural());
                    // not sure if we actually need to go recursive, it seems to mess up parameter order
                    //                    Set<Map.Entry> entrySet = ((Map)object).entrySet();
//                    for (Map.Entry entry : entrySet) {
//                        entry.setValue(sortMapByKey(entry.getValue()));
//                    }
                    sortedMap.putAll((Map)object);
                    return sortedMap;
                } else if (object instanceof List){
                    Collections.sort((List) object, (o1, o2) -> o1.toString().compareTo(o2.toString()));
                    return object;
                } else {
                    return object;
                }
            }
        }
    }
}
