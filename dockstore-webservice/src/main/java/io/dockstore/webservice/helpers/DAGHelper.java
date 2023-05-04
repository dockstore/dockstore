/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.helpers;

import com.google.gson.Gson;
import io.dockstore.webservice.core.dag.ElementsDefinition;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author gluu
 * @since 2019-10-02
 */
public final class DAGHelper {
    private static final Gson GSON = new Gson();

    private DAGHelper() {

    }

    /**
     * This removes edges which have undefined nodes
     *
     * @param dag The unclean DAG with edges that may have undefined nodes
     * @return The clean DAG without edges that have undefined nodes
     */
    public static String cleanDAG(String dag) {
        ElementsDefinition elementsDefinition = GSON.fromJson(dag, ElementsDefinition.class);
        if (elementsDefinition.nodes != null) {
            Set<String> nodeIDs = elementsDefinition.nodes.stream().map(nodeDefinition -> nodeDefinition.data.id).collect(Collectors.toSet());
            elementsDefinition.edges = elementsDefinition.edges.stream().filter(edgeDefinition -> nodeIDs.contains(edgeDefinition.data.source))
                .collect(Collectors.toList());
        }
        return GSON.toJson(elementsDefinition);
    }
}
