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

import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.OrcidAuthorDAO;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.xml.bind.JAXBException;
import org.apache.http.HttpStatus;
import org.orcid.jaxb.model.v3.release.record.Email;
import org.orcid.jaxb.model.v3.release.record.Person;
import org.orcid.jaxb.model.v3.release.record.summary.AffiliationGroup;
import org.orcid.jaxb.model.v3.release.record.summary.EmploymentSummary;
import org.orcid.jaxb.model.v3.release.record.summary.Employments;
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
                    OrcidAuthor orcidAuthor = createOrcidAuthor(orcidAuthorId, token);
                    long id = orcidAuthorDAO.create(orcidAuthor);
                    updatedOrcidAuthors.add(orcidAuthorDAO.findById(id));
                } catch (Exception ex) {
                    LOG.error("Could not get author information for ORCID author with ID {}", orcidAuthorId, ex);
                }
            }
        }
        version.setOrcidAuthors(updatedOrcidAuthors);
    }

    /**
     * Creates an OrcidAuthor, retrieving information about the author using the ORCID API.
     * @param id ORCID ID of the ORCID author
     * @param token ORCID token
     * @return OrcidAuthor object
     */
    public static OrcidAuthor createOrcidAuthor(String id, String token)
            throws URISyntaxException, IOException, InterruptedException, JAXBException {
        OrcidAuthor orcidAuthor = new OrcidAuthor(id);

        HttpResponse<String> response = ORCIDHelper.getPerson(id, token);
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOG.error("Could not get ORCID person with ID {}", id);
            return null;
        }

        Person person = ORCIDHelper.transformXmlToPerson(response.body());
        // Set name
        String fullName = person.getName().getGivenNames().getContent(); // At a minimum, the Orcid Author will have a first name because it's mandated by ORCID
        if (person.getName().getFamilyName() != null) {
            fullName += " " + person.getName().getFamilyName().getContent();
        }
        orcidAuthor.setName(fullName);
        // Set email
        Optional<Email> primaryEmail = person.getEmails().getEmails().stream().filter(email -> email.isPrimary()).findFirst();
        if (primaryEmail.isPresent()) {
            orcidAuthor.setEmail(primaryEmail.get().getEmail());
        }

        response = ORCIDHelper.getAllEmployments(id, token);
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOG.error("Could not get employment details for ORCID user with ID {}", id);
            return orcidAuthor; // Can still return the author since the author's name is set at this point
        }

        Employments employments = ORCIDHelper.transformXmlToEmployments(response.body());
        Collection<AffiliationGroup<EmploymentSummary>> affiliationGroups = employments.getEmploymentGroups();
        if (affiliationGroups.iterator().hasNext()) {
            // Set affiliation and role
            EmploymentSummary employmentSummary = affiliationGroups.iterator().next().getActivities().get(0); // The first employment in the list is the most recent according to end date
            orcidAuthor.setAffiliation(employmentSummary.getOrganization().getName());
            orcidAuthor.setRole(employmentSummary.getRoleTitle());
        }

        return orcidAuthor;
    }

    /**
     * Update an OrcidAuthor using the ORCID API.
     * @param orcidAuthor OrcidAuthor to update
     * @param token ORCID token
     */
    private static void updateOrcidAuthorProperties(OrcidAuthor orcidAuthor, String token) {
        try {
            OrcidAuthor updatedOrcidAuthor = createOrcidAuthor(orcidAuthor.getOrcid(), token);
            // Copy over updated properties
            orcidAuthor.setName(updatedOrcidAuthor.getName());
            orcidAuthor.setEmail(updatedOrcidAuthor.getEmail());
            orcidAuthor.setAffiliation(updatedOrcidAuthor.getAffiliation());
            orcidAuthor.setRole(updatedOrcidAuthor.getRole());
        } catch (Exception ex) {
            LOG.error("Could not update existing ORCID author with ORCID ID {}", orcidAuthor.getOrcid(), ex);
        }
    }
}
