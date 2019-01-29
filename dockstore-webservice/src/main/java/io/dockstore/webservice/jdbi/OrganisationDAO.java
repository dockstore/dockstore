package io.dockstore.webservice.jdbi;

import java.util.List;


import io.dockstore.webservice.core.Organisation;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class OrganisationDAO extends AbstractDAO<Organisation> {
    public OrganisationDAO(SessionFactory factory) {
        super(factory);
    }

    public Organisation findById(Long id) {
        return get(id);
    }

    public long create(Organisation organisation) {
        return persist(organisation).getId();
    }

    public long update(Organisation organisation) {
        return persist(organisation).getId();
    }

    public void delete(Organisation organisation) {
        Session session = currentSession();
        session.delete(organisation);
        session.flush();
    }

    public List<Organisation> findAllApproved() {
        return list(namedQuery("io.dockstore.webservice.core.Organisation.findAllApproved"));
    }

    public List<Organisation> findAllPending() {
        return list(namedQuery("io.dockstore.webservice.core.Organisation.findAllPending"));
    }

    public List<Organisation> findAllRejected() {
        return list(namedQuery("io.dockstore.webservice.core.Organisation.findAllRejected"));
    }

    public List<Organisation> findAll() {
        return list(namedQuery("io.dockstore.webservice.core.Organisation.findAll"));
    }


    public Organisation findByName(String name) {
        Query query =  namedQuery("io.dockstore.webservice.core.Organisation.findByName")
                .setParameter("name", name);
        return uniqueResult(query);
    }

    public Organisation findApprovedByName(String name) {
        Query query =  namedQuery("io.dockstore.webservice.core.Organisation.findApprovedByName")
                .setParameter("name", name);
        return uniqueResult(query);
    }

    public Organisation findApprovedById(Long id) {
        Query query =  namedQuery("io.dockstore.webservice.core.Organisation.findApprovedById")
                .setParameter("id", id);
        return uniqueResult(query);
    }


}
