package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.Collection;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class CollectionDAO extends AbstractDAO<Collection> {
    public CollectionDAO(SessionFactory factory) {
        super(factory);
    }

    public Collection findById(Long id) {
        return get(id);
    }

    public long create(Collection collection) {
        return persist(collection).getId();
    }

    public long update(Collection collection) {
        return persist(collection).getId();
    }

    public void delete(Collection collection) {
        Session session = currentSession();
        session.delete(collection);
        session.flush();
    }

    public List<Collection> findAllByOrg(long organisationId) {
        Query query = namedQuery("io.dockstore.webservice.core.Collection.findAllByOrg")
                .setParameter("organisationId", organisationId);
        return list(query);
    }

    public Collection findByNameAndOrg(String name, long organisationId) {
        Query query = namedQuery("io.dockstore.webservice.core.Collection.findByNameAndOrg")
                .setParameter("name", name)
                .setParameter("organisationId", organisationId);
        return uniqueResult(query);
    }

}
