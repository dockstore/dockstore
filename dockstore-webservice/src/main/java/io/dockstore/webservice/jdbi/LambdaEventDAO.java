package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class LambdaEventDAO extends AbstractDAO<LambdaEvent> {
    public LambdaEventDAO(SessionFactory factory) {
        super(factory);
    }

    public LambdaEvent findById(Long id) {
        return get(id);
    }

    public long create(LambdaEvent lambdaEvent) {
        return persist(lambdaEvent).getId();
    }

    public long update(LambdaEvent lambdaEvent) {
        return persist(lambdaEvent).getId();
    }

    public void delete(LambdaEvent lambdaEvent) {
        Session session = currentSession();
        session.delete(lambdaEvent);
        session.flush();
    }

    public List<LambdaEvent> findByRepository(String repository) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByRepository")
                .setParameter("repository", repository);
        return list(query);
    }

    public List<LambdaEvent> findByUsername(String username) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByUsername")
                .setParameter("username", username);
        return list(query);
    }

    public List<LambdaEvent> findByUser(User user) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByUser")
                .setParameter("user", user);
        return list(query);
    }

    public List<LambdaEvent> findByOrganization(String organization) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByOrganization")
                .setParameter("organization", organization + "/%");
        return list(query);
    }
}
