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

package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.database.UserInfo;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public class UserDAO extends AbstractDockstoreDAO<User> {
    private static final Logger LOG = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO(SessionFactory factory) {
        super(factory);
    }

    public User findById(Long id) {
        return get(id);
    }

    public long create(User user) {
        return persist(user).getId();
    }

    public void clearCache() {
        currentSession().flush();
        currentSession().clear();
    }

    public List<User> findAll() {
        return list(namedTypedQuery("io.dockstore.webservice.core.User.findAll"));
    }

    public List<Long> findAllGitHubUserIds() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.User.findAllGitHubUserIds"));
    }

    /**
     * Deprecated method, is mostly likely dangerous if the username can be changed
     *
     * @param username username of user to find
     * @return username
     * @deprecated likely dangerous to use with changing usernames
     */
    @Deprecated
    public User findByUsername(String username) {
        Query<User> query = namedTypedQuery("io.dockstore.webservice.core.User.findByUsername").setParameter("username", username);
        return query.uniqueResult();
    }

    /**
     * Return starred entries for a particular end user
     * @param entryTypes
     * @param username
     * @return
     */
    public Set<Entry> findStarredEntries(List<Class> entryTypes, String username) {
        final Query query = namedTypedQuery("io.dockstore.webservice.core.User.findStarredEntries").setParameter("entryTypes", entryTypes).setParameter("username", username);
        List<Entry> list = query.list();
        return Set.copyOf(list);
    }

    public User findByGoogleEmail(String email) {
        final Query<User> query = namedTypedQuery("io.dockstore.webservice.core.User.findByGoogleEmail")
            .setParameter("email", email);
        return query.uniqueResult();
    }

    public User findByGoogleOnlineProfileId(String id) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.User.findByGoogleUserId").setParameter("id", id));
    }

    public User findByGitHubUsername(String username) {
        final Query<User> query = namedTypedQuery("io.dockstore.webservice.core.User.findByGitHubUsername")
            .setParameter("username", username);
        return query.uniqueResult();
    }

    public User findByGitHubUserId(String id) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.User.findByGitHubUserId").setParameter("id", id));
    }

    public long findPublishedEntries(String username)  {
        final Query query = namedQuery("io.dockstore.webservice.core.User.countPublishedEntries").setParameter("username", username);
        return (long)query.uniqueResult();
    }

    public boolean delete(User user) {
        try {
            // user.getUserProfiles().values().forEach(profile -> currentSession().delete(profile));
            //TODO: might want to clean up better later, but prototype for now
            currentSession().delete(user);
            currentSession().flush();
        } catch (Exception e) {
            LOG.error("something happened with delete, probably cascades are broken", e);
            return false;
        }
        return true;
    }

    public List<UserInfo> findAllGitHubUserInfo() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.database.UserInfo.findAllGitHubUserInfo"));
    }
    public List<UserInfo> findAllGoogleUserInfo() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.database.UserInfo.findAllGoogleUserInfo"));
    }
}
