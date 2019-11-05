package io.dockstore.webservice.resources;

import java.util.Optional;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowVersionResource implements AuthenticatedResourceInterface, AliasableResourceInterface<WorkflowVersion> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowVersionResource.class);
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final WorkflowDAO workflowDAO;
    private final WorkflowResource workflowResource;


    public WorkflowVersionResource(SessionFactory sessionFactory, WorkflowResource workflowResource) {
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowResource = workflowResource;
    }

    @Override
    public Optional<PublicStateManager> getPublicStateManager() {
        return Optional.empty();
    }

    @Override
    public WorkflowVersion getAndCheckResource(User user, Long workflowVersionId) {
        final WorkflowVersion workflowVersion = this.workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            LOG.error("Could not find workflow version using the workflow version id: " + workflowVersionId);
            throw new CustomWebApplicationException("Workflow version not found when searching with id: " + workflowVersionId, HttpStatus.SC_BAD_REQUEST);
        }

        long workflowId = workflowDAO.getWorkflowIdByWorkflowVersionId(workflowVersionId);
        Workflow workflow = workflowDAO.findById(workflowId);
        workflowResource.checkEntry(workflow);
        workflowResource.checkUserCanUpdate(user, workflow);
        return workflowVersion;
    }

    @Override
    // TODO: EntryResource.java also throws an exception for this method; need good explanation for why
    public WorkflowVersion getAndCheckResourceByAlias(String alias) {
        throw new UnsupportedOperationException("Use the TRS API for tools and workflows");
    }

}
