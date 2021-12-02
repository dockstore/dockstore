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

package io.dockstore.webservice.core.dag;

import java.util.List;

/**
 * @author gluu
 * @since 2019-10-01
 */
public class ElementsDefinition {
    public List<NodeDefinition> nodes;
    public List<EdgeDefinition> edges;

    public static class EdgeDefinition {
        public EdgeDataDefinition data;

        public static class EdgeDataDefinition {
            public String id;

            /**
             * the source node id (edge comes from this node)
             */
            public String source;
            /**
             * the target node id (edge goes to this node)
             */
            public String target;
        }
    }
    public static class NodeDefinition {
        public NodeDataDefinition data;

        public static class NodeDataDefinition {
            public String name;
            public String run;
            public String id;
            public String type;
            public String tool;
            public String docker;
        }
    }
}
