package io.dockstore.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.quay.client.model.PullRobot;
import org.junit.Assert;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class QuayModelTest {
    @Test
    public void testPullRobot() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        PullRobot pullRobot = objectMapper.readValue(fixture("fixtures/pullRobot.json"), PullRobot.class);
        Assert.assertEquals("user", pullRobot.getKind());
        Assert.assertEquals("ucsc_cgl+robot", pullRobot.getName());
        Assert.assertEquals(true, pullRobot.isIsRobot());
    }
}
