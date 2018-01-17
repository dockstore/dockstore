/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.jdbi;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.Workflow;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import java.util.List;

/**
 * @author dyuen
 */
public class WorkflowDAO extends EntryDAO<Workflow> {
    private final int sourcecontrolIndex = 0;
    private final int organizationIndex = 1;
    private final int repositoryIndex = 2;
    private final int workflownameIndex = 3;

    public WorkflowDAO(SessionFactory factory) {
        super(factory);
    }

    public Workflow findByPath(String path, boolean findPublished) {
        Object[] splitPath = Workflow.splitWorkflowPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        SourceControl sourcecontrol = (SourceControl)splitPath[sourcecontrolIndex];
        String organization = (String)splitPath[organizationIndex];
        String repository = (String)splitPath[repositoryIndex];
        String workflowname = (String)splitPath[workflownameIndex];


        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Workflow.";

        if (splitPath[workflownameIndex] == null) {
            if (findPublished) {
                fullQueryName += "findPublishedByPathNullWorkflowName";
            } else {
                fullQueryName += "findByPathNullWorkflowName";
            }

        } else {
            if (findPublished) {
                fullQueryName += "findPublishedByPath";
            } else {
                fullQueryName += "findByPath";
            }
        }

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("sourcecontrol", sourcecontrol)
            .setParameter("organization", organization)
            .setParameter("repository", repository);

        if (splitPath[workflownameIndex] != null) {
            query.setParameter("workflowname", workflowname);
        }

        return uniqueResult(query);
    }


    public List<Workflow> findByGitUrl(String giturl) {
        return list(namedQuery("io.dockstore.webservice.core.Workflow.findByGitUrl")
            .setParameter("gitUrl", giturl));
    }

    public List<Workflow> findPublishedByOrganization(String organization) {
        return list(namedQuery("io.dockstore.webservice.core.Workflow.findPublishedByOrganization")
            .setParameter("organization", organization));
    }
}
