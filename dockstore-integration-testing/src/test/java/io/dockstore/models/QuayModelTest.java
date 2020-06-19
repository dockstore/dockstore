package io.dockstore.models;

import com.google.gson.Gson;
import io.swagger.quay.client.model.PullRobot;
import org.junit.Assert;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class QuayModelTest {

    @Test
    public void testPullRobot() {
        String fixture = fixture("fixtures/pullRobot.json");
        Gson gson = new Gson();
        PullRobot pullRobot = gson.fromJson(fixture, PullRobot.class);
        Assert.assertEquals("user", pullRobot.getKind());
        Assert.assertEquals("ucsc_cgl+robot", pullRobot.getName());
        Assert.assertEquals(true, pullRobot.isIsRobot());
    }
}
