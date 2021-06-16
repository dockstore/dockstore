package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.Organization;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class OrganizationDAO extends AbstractDAO<Organization> {
    public OrganizationDAO(SessionFactory factory) {
        super(factory);
    }

    public Organization findById(Long id) {
        return get(id);
    }

    public long create(Organization organization) {
        return persist(organization).getId();
    }

    public long update(Organization organization) {
        return persist(organization).getId();
    }

    public void delete(Organization organization) {
        Session session = currentSession();
        session.delete(organization);
        session.flush();
    }

    public List<Organization> findApprovedSortedByStar() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Organization.findApprovedSortedByStar"));
    }
    public List<Organization> findAllApproved() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Organization.findAllApproved"));
    }

    public List<Organization> findAllPending() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Organization.findAllPending"));
    }

    public List<Organization> findAllRejected() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Organization.findAllRejected"));
    }

    public List<Organization> findAll() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Organization.findAll"));
    }


    public Organization findByName(String name) {
        Query<Organization> query = namedTypedQuery("io.dockstore.webservice.core.Organization.findByName").setParameter("name", name);
        return uniqueResult(query);
    }

    public Organization findApprovedByName(String name) {
        Query<Organization> query =  namedTypedQuery("io.dockstore.webservice.core.Organization.findApprovedByName")
                .setParameter("name", name);
        return uniqueResult(query);
    }

    public Organization findApprovedById(Long id) {
        Query<Organization> query =  namedTypedQuery("io.dockstore.webservice.core.Organization.findApprovedById")
                .setParameter("id", id);
        return uniqueResult(query);
    }

    public Organization getByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Organization.getByAlias").setParameter("alias", alias));
    }
}
