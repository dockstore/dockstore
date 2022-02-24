/*
 *    Copyright 2022 OICR and UCSC
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

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.OrcidAuthorDAO;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OrcidAuthorHelper {

    private static final Logger LOG = LoggerFactory.getLogger(OrcidAuthorHelper.class);

    private OrcidAuthorHelper() { }

    /**
     * Update the ORCID authors for the version
     * @param version Version to update ORCID authors for
     * @param orcidAuthorIds A set of ORCID author IDs
     * @param token An ORCID token
     * @param orcidAuthorDAO ORCID Author DAO
     */
    public static void updateVersionOrcidAuthors(Version version, Set<String> orcidAuthorIds, String token, OrcidAuthorDAO orcidAuthorDAO) {
        Set<OrcidAuthor> updatedOrcidAuthors = new HashSet<>();
        for (String orcidAuthorId : orcidAuthorIds) {
            OrcidAuthor existingOrcidAuthor = orcidAuthorDAO.findByOrcidId(orcidAuthorId);
            if (existingOrcidAuthor != null) {
                updateOrcidAuthorProperties(existingOrcidAuthor, token);
                updatedOrcidAuthors.add(existingOrcidAuthor);
            } else {
                try {
                    OrcidAuthor orcidAuthor = ORCIDHelper.createOrcidAuthor(orcidAuthorId, token);
                    long id = orcidAuthorDAO.create(orcidAuthor);
                    updatedOrcidAuthors.add(orcidAuthorDAO.findById(id));
                } catch (URISyntaxException | IOException | InterruptedException | JAXBException | CustomWebApplicationException ex) {
                    LOG.error("Could not get author information for ORCID author with ID {}", orcidAuthorId, ex);
                }
            }
        }
        version.setOrcidAuthors(updatedOrcidAuthors);
    }

    /**
     * Update an OrcidAuthor using the ORCID API.
     * @param orcidAuthor OrcidAuthor to update
     * @param token ORCID token
     */
    private static void updateOrcidAuthorProperties(OrcidAuthor orcidAuthor, String token) {
        try {
            OrcidAuthor updatedOrcidAuthor = ORCIDHelper.createOrcidAuthor(orcidAuthor.getOrcid(), token);
            // Copy over updated properties
            orcidAuthor.setName(updatedOrcidAuthor.getName());
            orcidAuthor.setEmail(updatedOrcidAuthor.getEmail());
            orcidAuthor.setAffiliation(updatedOrcidAuthor.getAffiliation());
            orcidAuthor.setRole(updatedOrcidAuthor.getRole());
        } catch (URISyntaxException | IOException | InterruptedException | JAXBException | CustomWebApplicationException ex) {
            LOG.error("Could not update existing ORCID author with ORCID ID {}", orcidAuthor.getOrcid(), ex);
        }
    }
}
