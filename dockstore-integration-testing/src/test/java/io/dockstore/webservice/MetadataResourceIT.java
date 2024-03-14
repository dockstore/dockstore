/*
 * Copyright 2022 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.HealthCheckResult;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetadataResourceIT extends BaseIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (Exception e) {
            // This space intentionally left blank.
        }
    }

    void makeElasticsearchConsistent() {

        ApiClient apiClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(apiClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        // Retrieve all the published entries
        List<DockstoreTool> tools = containersApi.allPublishedContainers(null, null, null, null, null);
        List<Workflow> workflows = workflowsApi.allPublishedWorkflows(null, null, null, null, null, false, null);

        // Unpublish the entries
        PublishRequest unpublishRequest = CommonTestUtilities.createOpenAPIPublishRequest(false);
        tools.forEach(tool -> containersApi.publish(tool.getId(), unpublishRequest));
        workflows.forEach(workflow -> workflowsApi.publish1(workflow.getId(), unpublishRequest));

        // Give the ES server a few seconds to deindex everything
        sleep(5000);
    }

    @Test
    void testCheckHealth() {
        makeElasticsearchConsistent();

        ApiClient anonymousApiClient = getAnonymousOpenAPIWebClient();
        MetadataApi metadataApi = new MetadataApi(anonymousApiClient);

        List<HealthCheckResult> results;
        List<String> healthCheckNames;
        try {
            results = metadataApi.checkHealth(null);
            // Not asserting size because there seems to be an JdbiHealthCheck present when running tests
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            for (String name: List.of("hibernate", "deadlocks", "connectionPool", "liquibaseLock", "elasticsearchConsistency")) {
                assertTrue(healthCheckNames.contains(name), String.format("health check names should include '%s'", name));
            }
        } catch (ApiException e) {
            fail("Health checks should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("hibernate"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("hibernate"));
        } catch (ApiException e) {
            fail("Hibernate health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("deadlocks"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("deadlocks"));
        } catch (ApiException e) {
            fail("Deadlocks health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("connectionPool"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("connectionPool"));
        } catch (ApiException e) {
            fail("Connection pool health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("hibernate", "connectionPool"));
            assertEquals(2, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("connectionPool"));
        } catch (ApiException e) {
            fail("Hibernate and connection pool health checks should not fail");
        }

        assertThrows(ApiException.class, () -> metadataApi.checkHealth(List.of("foobar")));
    }

    @Test
    void testEntryTypeMetadataList() {
        ApiClient anonymousApiClient = getAnonymousOpenAPIWebClient();
        MetadataApi metadataApi = new MetadataApi(anonymousApiClient);
        assertEquals(io.dockstore.webservice.core.EntryTypeMetadata.values().size(), metadataApi.getEntryTypeMetadataList().size());
    }
}
