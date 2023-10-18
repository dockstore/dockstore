/*
 * Copyright 2023 OICR, UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.database.NotebookPath;
import io.dockstore.webservice.core.database.RSSNotebookPath;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.hibernate.SessionFactory;

public class NotebookDAO extends EntryDAO<Notebook> {
    public NotebookDAO(SessionFactory factory) {
        super(factory);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected Root<Notebook> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q) {

        final SourceControlConverter converter = new SourceControlConverter();
        final Root<Notebook> entryRoot = q.from(Notebook.class);

        Predicate predicate = getWorkflowPredicate(descriptorLanguage, registry, organization, name, toolname, description, checker, cb, converter, entryRoot);

        // notebook is never a checker workflow
        if (checker != null && checker) {
            predicate = cb.isFalse(cb.literal(true));
        }

        q.where(predicate);
        return entryRoot;
    }

    public List<NotebookPath> findAllPublishedPaths() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Notebook.findAllPublishedPaths", NotebookPath.class).list();
    }

    public List<RSSNotebookPath> findAllPublishedPathsOrderByDbupdatedate() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Notebook.findAllPublishedPathsOrderByDbupdatedate", RSSNotebookPath.class).setMaxResults(
                RSS_ENTRY_LIMIT).list();
    }
}
