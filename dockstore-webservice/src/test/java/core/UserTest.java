/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.core.User;
import io.dropwizard.jackson.Jackson;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;

/**
 *
 * @author xliu
 */
public class UserTest {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    private User getUser() {
        final User user = new User();
        user.setUsername("victoroicr");
        user.setIsAdmin(false);

        return user;
    }

    @Test
    public void serializesToJson() throws Exception {
        final User user = getUser();
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/user.json"), User.class));
        assertThat(MAPPER.writeValueAsString(user)).isEqualTo(expected);
    }

    @Test
    public void deserializesFromJSON() throws Exception {
        final User user = getUser();
        assertThat(MAPPER.readValue(fixture("fixtures/user.json"), User.class)).isEqualTo(user);
    }

}
