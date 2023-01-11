package io.dockstore.models;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.api.BuildApi;
import io.swagger.quay.client.model.InlineResponse2001;
import io.swagger.quay.client.model.PullRobot;
import io.swagger.quay.client.model.QuayBuild;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuayModelTest {

    /**
     * Tests that the swagger-generated Quay.io api can deserialize a sample PullRobot JSON (that isn't just null)
     * @throws JsonProcessingException  If cannot deserialize
     */
    @Test
    void testPullRobot() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        PullRobot pullRobot = objectMapper.readValue(fixture("fixtures/pullRobot.json"), PullRobot.class);
        pullRobotAssertions(pullRobot);
    }

    /**
     * Test that the swagger-generated Quay.io api can deserialize a sample InlineResponse2001 JSON that has pull_robot that isn't just null
     * The sample JSON is the String response observed from testGetRepoBuildsWithPullRobot
     * @throws JsonProcessingException If cannot deserialize
     */
    @Test
    void testInlineResponse2001() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        InlineResponse2001 inlineResponse2001 = objectMapper.readValue(fixture("fixtures/inlineResponse2001.json"), InlineResponse2001.class);
        inlineResponse2001.getBuilds().forEach(build -> {
            PullRobot pullRobot = build.getPullRobot();
            pullRobotAssertions(pullRobot);
        });
    }

    /**
     * This test is identical to testInlineResponse2001 except it actually pulls from Quay.io's ucsc_cgl/kallisto which is currently known
     * have pull_robot that is not null
     * @throws ApiException If cannot deserialize or reach Quay.io
     */
    @Test
    void testGetRepoBuildsWithPullRobot() throws ApiException {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        BuildApi buildApi = new BuildApi(apiClient);
        final List<QuayBuild> builds = buildApi.getRepoBuilds("ucsc_cgl/kallisto", null, 5).getBuilds();
        for (QuayBuild build : builds) {
            PullRobot pullRobot = build.getPullRobot();
            pullRobotAssertions(pullRobot);
        }
    }

    private void pullRobotAssertions(PullRobot pullRobot) {
        assertEquals("user", pullRobot.getKind());
        assertEquals("ucsc_cgl+robot", pullRobot.getName());
        assertEquals(true, pullRobot.isIsRobot());
    }
}
