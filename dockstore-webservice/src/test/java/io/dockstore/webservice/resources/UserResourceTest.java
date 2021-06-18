package io.dockstore.webservice.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import io.dockstore.webservice.CustomWebApplicationException;
import org.junit.Assert;
import org.junit.Test;

public class UserResourceTest {

    @Test
    public void usernameRestrictionsTest() {
        List<String> invalidUsernames = new ArrayList<>(List.of("dockstore", "DOCKSTORE", "thisIsDoCkStOrE", "admin", "aaaaadminbbb", "curator",
                "asdflkCURATORSrrr", "system", "SYSTEMMMMMM", "manager", "withoutaMANAGER"));
        for (String username : invalidUsernames) {
            try {
                UserResource.restrictUsername(username);
                Assert.fail("Should not be able to create a username with a keyword.");
            } catch (CustomWebApplicationException ex) {
                Assert.assertTrue(ex.errorMessage.contains("because it contains one or more of the following keywords:"));
            }
        }
    }

    @Test
    public void gitHubIdRegexTest() {
        List<String> avatarUrls = new ArrayList<>(List.of("https://avatars3.githubusercontent.com/u/11111?v=4", "https://avatars2.githubusercontent.com/u/1234567?v=4",
                "https://avatars.githubusercontent.com/u/999999999?v=4", "https://avatars3.githubusercontent.com/u/987654?v=3"));
        List<String> ids = new ArrayList<>(List.of("11111", "1234567", "999999999", "987654"));
        for (String avatarUrl : avatarUrls) {
            Matcher m = UserResource.GITHUB_ID_PATTERN.matcher(avatarUrl);
            Assert.assertTrue(m.matches());
            Assert.assertTrue(ids.stream().anyMatch(id -> id.equals(m.group(1))));
        }
    }

}
