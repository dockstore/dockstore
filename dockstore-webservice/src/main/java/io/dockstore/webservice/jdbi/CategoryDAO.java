package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Collection;
import java.util.List;
import java.util.stream.Collectors;
// import javax.persistence.NoResultException;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

@SuppressWarnings("checkstyle:magicnumber")
public class CategoryDAO extends AbstractDockstoreDAO<Collection> {
    public CategoryDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Category> getCategories() {
        return (list(currentSession().getNamedQuery("io.dockstore.webservice.core.Category.getCategories")));
    }

    public List<String> getCategoryNames() {
        List<Category> categories = getCategories();
        return (categories.stream().map(c -> c.getName()).collect(Collectors.toList()));
    }

    public Category findByName(String name) {
        Query query = namedTypedQuery("io.dockstore.webservice.core.Category.findByName")
            .setParameter("name", name);
        return ((Category)uniqueResult(query));
    }
}
