/*
 *    Copyright 2017 OICR
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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.FixtureUtility;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.User;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

/**
 * @author xliu
 */
class UserTest {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    static {
        DockstoreWebserviceApplication.configureMapper(MAPPER);
    }

    private User getUser() {
        final User user = new User();
        user.setUsername("victoroicr");
        user.setIsAdmin(false);

        return user;
    }

    @Test
    void serializesToJson() throws Exception {
        final User user = getUser();
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(FixtureUtility.fixture("fixtures/user.json"), User.class));
        assertThat(MAPPER.writeValueAsString(user)).isEqualTo(expected);
    }

    @Test
    void deserializesFromJSON() throws Exception {
        final User user = getUser();
        assertThat(MAPPER.readValue(FixtureUtility.fixture("fixtures/user.json"), User.class)).isEqualTo(user);
    }

}
