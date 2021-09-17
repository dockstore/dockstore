package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Category;
import java.util.List;
import org.hibernate.SessionFactory;

@SuppressWarnings("checkstyle:magicnumber")
public class CategoryDAO extends AbstractDockstoreDAO<Category> {
    public CategoryDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Category> getCategories() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Category.getCategories"));
    }

    public Category findByName(String name) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Category.findByName").setParameter("name", name));
    }
}
