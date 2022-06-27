/*
 * Copyright 2021 OICR and UCSC
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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.SourceControlConverter;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.hibernate.SessionFactory;

public class AppToolDAO extends EntryDAO<AppTool> {
    public AppToolDAO(SessionFactory factory) {
        super(factory);
    }

    @SuppressWarnings({"checkstyle:ParameterNumber"})
    protected Root<AppTool> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q) {

        final SourceControlConverter converter = new SourceControlConverter();
        final Root<AppTool> entryRoot = q.from(AppTool.class);

        Predicate predicate = getWorkflowPredicate(descriptorLanguage, registry, organization, name, toolname, description, author, checker, cb, converter, entryRoot);

        // apptool is never a checker workflow
        if (checker != null && checker) {
            predicate = cb.isFalse(cb.literal(true));
        }

        q.where(predicate);
        return entryRoot;
    }
}
