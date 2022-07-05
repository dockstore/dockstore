/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.jdbi;

import static io.dockstore.webservice.resources.MetadataResource.RSS_ENTRY_LIMIT;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.database.MyWorkflows;
import io.dockstore.webservice.core.database.RSSWorkflowPath;
import io.dockstore.webservice.core.database.WorkflowPath;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.hibernate.SessionFactory;

/**
 * @author gluu
 * @since 2019-09-11
 */
public class BioWorkflowDAO extends EntryDAO<BioWorkflow> {
    public BioWorkflowDAO(SessionFactory factory) {
        super(factory);
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    protected Root<BioWorkflow> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q) {

        final SourceControlConverter converter = new SourceControlConverter();
        final Root<BioWorkflow> entryRoot = q.from(BioWorkflow.class);

        Predicate predicate = getWorkflowPredicate(descriptorLanguage, registry, organization, name, toolname, description, author, checker, cb, converter, entryRoot);

        // its tempting to put this in EntryDAO, but something goes wrong with generics/inheritance
        if (checker != null) {
            predicate = cb.and(predicate, cb.equal(entryRoot.get("isChecker"), checker));
        }
        q.where(predicate);
        return entryRoot;
    }

    public List<WorkflowPath> findAllPublishedPaths() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.BioWorkflow.findAllPublishedPaths"));
    }

    public List<RSSWorkflowPath> findAllPublishedPathsOrderByDbupdatedate() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.BioWorkflow.findAllPublishedPathsOrderByDbupdatedate").setMaxResults(
                RSS_ENTRY_LIMIT));
    }

    public List<MyWorkflows> findUserBioWorkflows(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.BioWorkflow.findUserBioWorkflows").setParameter("userId", userId));
    }
}
