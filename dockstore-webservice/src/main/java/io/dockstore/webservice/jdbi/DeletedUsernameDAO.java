package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.DeletedUsername;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class DeletedUsernameDAO extends AbstractDAO<DeletedUsername> {
    public DeletedUsernameDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public DeletedUsername findById(Long id) {
        return get(id);
    }

    public long create(DeletedUsername deletedUsername) {
        return persist(deletedUsername).getId();
    }

    public void delete(DeletedUsername deletedUsername) {
        Session session = currentSession();
        session.delete(deletedUsername);
        session.flush();
    }

    public DeletedUsername findByUsername(String username) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.DeletedUsername.findByUsername").setParameter("username", username));
    }
}
