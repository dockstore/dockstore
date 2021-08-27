package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Collection;
// import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import javax.persistence.NoResultException;
// import org.hibernate.Session;
import org.hibernate.SessionFactory;

// import org.hibernate.query.Query;

@SuppressWarnings("checkstyle:magicnumber")
public class CategoryDAO extends AbstractDockstoreDAO<Collection> {
    public CategoryDAO(SessionFactory factory) {
        super(factory);
    }

    public List<String> getCategoryNames() {
        return list(currentSession().getNamedNativeQuery("io.dockstore.webservice.core.Collection.getCategoryNames"));
    }

    public Long getSpecialOrganizationId() {
        try {
            return ((Number)currentSession().getNamedQuery("io.dockstore.webservice.core.Collection.getSpecialOrganizationId").getSingleResult()).longValue();
        } catch (NoResultException e) {
            return (null);
        }
    }
}
