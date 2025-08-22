package io.dockstore.webservice.jdbi;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.GitHubAppNotification;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class GitHubAppNotificationDAO extends AbstractDAO<GitHubAppNotification> {
    public GitHubAppNotificationDAO(SessionFactory factory) {
        super(factory);
    }

    public long create(GitHubAppNotification notification) {
        return persist(notification).getId();
    }

    public GitHubAppNotification update(GitHubAppNotification notification) {
        Session session = currentSession();
        session.update(notification);
        session.flush();
        return notification;
    }

    public void delete(GitHubAppNotification notification) {
        Session session = currentSession();
        session.delete(notification);
        session.flush();
    }

    public GitHubAppNotification findById(long id) {
        return get(id);
    }

    public GitHubAppNotification findLatestByRepository(SourceControl sourceControl, String organization, String repository) {
        return currentSession().createNamedQuery("io.dockstore.webservice.core.GitHubAppNotification.getLatestByRepository", GitHubAppNotification.class).setParameter("sourcecontrol", sourceControl).setParameter("organization", organization).setParameter("repository", repository).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    public List<GitHubAppNotification> findByUser(User user) {
        Query<GitHubAppNotification> query = namedTypedQuery("io.dockstore.webservice.core.GitHubAppNotification.findByUser")
            .setParameter("user", user);
        return list(query);
    }
}
