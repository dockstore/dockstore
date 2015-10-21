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
package io.dockstore.webservice.jdbi;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import java.util.Random;
import org.hibernate.Query;
import org.hibernate.SessionFactory;

/**
 *
 * @author xliu
 */
public class UserDAO extends AbstractDAO<User> {
    public UserDAO(SessionFactory factory) {
        super(factory);
    }

    public User findById(Long id) {
        return get(id);
    }

    public long create(User user) {
        final Random random = new Random();
        final int bufferLength = 1024;
        final byte[] buffer = new byte[bufferLength];
        random.nextBytes(buffer);
        String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
        final String hashedPassword = Hashing.sha256().hashString(user.getUsername() + randomString, Charsets.UTF_8).toString();

        user.setHashedPassword(hashedPassword);

        return persist(user).getId();
    }

    public List<User> findAll() {
        return list(namedQuery("io.dockstore.webservice.core.User.findAll"));
    }

    public User findByUsername(String username) {
        Query query = namedQuery("io.dockstore.webservice.core.User.findByUsername").setParameter("username", username);
        User user = (User) query.uniqueResult();
        return user;
    }

    public User findUserByHashedPassword(String hashedPassword) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.User.findByPassword").setString("hashedPassword", hashedPassword));
    }
}
