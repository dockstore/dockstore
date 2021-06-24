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

import static io.dropwizard.testing.FixtureHelpers.fixture;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 1.8.0
 */
public class DAGHelperTest {

    /**
     * Tests that a unclean DAG is converted to a clean one.
     * Also tests that a clean DAG is unaffected.
     */
    @Test
    public void cleanDAGTest() {
        String uncleanDAG = fixture("fixtures/uncleanDAG.json");
        String cleanDAG = DAGHelper.cleanDAG(uncleanDAG);
        Assert.assertEquals(fixture("fixtures/cleanDAG.json").replace(" ", "").replace("\n", ""), cleanDAG.trim());
        String cleanerDAG = DAGHelper.cleanDAG(cleanDAG);
        Assert.assertEquals(fixture("fixtures/cleanDAG.json").replace(" ", "").replace("\n", ""), cleanerDAG.trim());
    }
}
