package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Collection;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

@SuppressWarnings("checkstyle:magicnumber")
public class CategoryDAO extends AbstractDockstoreDAO<Collection> {
    public CategoryDAO(SessionFactory factory) {
        super(factory);
    }

    // TODO make the query invocations consistent with rest of code
    public List<Category> getCategories() {
        return (list(currentSession().getNamedQuery("io.dockstore.webservice.core.Category.getCategories")));
    }

    public Category findByName(String name) {
        Query query = namedTypedQuery("io.dockstore.webservice.core.Category.findByName").setParameter("name", name);
        return ((Category)uniqueResult(query));
    }
}
