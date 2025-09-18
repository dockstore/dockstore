package io.dockstore.webservice.jdbi;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.GitHubAppNotification;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

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

    public GitHubAppNotification findLatestByRepositoryIncludingHidden(SourceControl sourceControl, String organization, String repository) {
        return currentSession().createNamedQuery("io.dockstore.webservice.core.GitHubAppNotification.getLatestByRepositoryIncludingHidden", GitHubAppNotification.class).setParameter("sourcecontrol", sourceControl).setParameter("organization", organization).setParameter("repository", repository).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    public List<GitHubAppNotification> findByUser(User user) {
        return findByUser(user, 0, Integer.MAX_VALUE);
    }

    public List<GitHubAppNotification> findByUser(User user, Integer offset, Integer limit) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<GitHubAppNotification> query = cb.createQuery(GitHubAppNotification.class);
        Root<GitHubAppNotification> userNotificationRoot = query.from(GitHubAppNotification.class);
        query.select(userNotificationRoot)
            .where(cb.equal(userNotificationRoot.get("user"), user), cb.isFalse(userNotificationRoot.get("hidden")))
            .orderBy(cb.desc(userNotificationRoot.get("dbCreateDate")));
        return currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long getCountByUser(User user) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.GitHubAppNotification.getCountByUser", Long.class)
            .setParameter("user", user)
            .getSingleResult();
    }
}
