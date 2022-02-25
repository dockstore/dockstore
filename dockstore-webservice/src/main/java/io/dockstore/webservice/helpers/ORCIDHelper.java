package io.dockstore.webservice.helpers;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static java.net.http.HttpRequest.BodyPublishers.ofString;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Version;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.orcid.jaxb.model.common.Relationship;
import org.orcid.jaxb.model.common.WorkType;
import org.orcid.jaxb.model.v3.release.common.CreatedDate;
import org.orcid.jaxb.model.v3.release.common.LastModifiedDate;
import org.orcid.jaxb.model.v3.release.common.Title;
import org.orcid.jaxb.model.v3.release.common.Url;
import org.orcid.jaxb.model.v3.release.record.Email;
import org.orcid.jaxb.model.v3.release.record.ExternalID;
import org.orcid.jaxb.model.v3.release.record.ExternalIDs;
import org.orcid.jaxb.model.v3.release.record.Person;
import org.orcid.jaxb.model.v3.release.record.Work;
import org.orcid.jaxb.model.v3.release.record.WorkTitle;
import org.orcid.jaxb.model.v3.release.record.summary.AffiliationGroup;
import org.orcid.jaxb.model.v3.release.record.summary.EmploymentSummary;
import org.orcid.jaxb.model.v3.release.record.summary.Employments;
import org.orcid.jaxb.model.v3.release.record.summary.WorkGroup;
import org.orcid.jaxb.model.v3.release.record.summary.WorkSummary;
import org.orcid.jaxb.model.v3.release.record.summary.Works;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Swagger-ui available here: https://api.orcid.org/v3.0/#!/Development_Member_API_v3.0/
public final class ORCIDHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ORCIDHelper.class);
    private static final String ORCID_XML_CONTENT_TYPE = "application/vnd.orcid+xml";
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    private static String baseApiUrl; // baseApiUrl should result in something like "https://api.sandbox.orcid.org/v3.0/" or "https://api.orcid.org/v3.0/"
    private static String baseUrl; // baseUrl should be something like "https://sandbox.orcid.org/" or "https://orcid.org/"
    private static String orcidClientId;
    private static String orcidClientSecret;

    private ORCIDHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration configuration) {
        try {
            URL orcidAuthUrl = new URL(configuration.getUiConfig().getOrcidAuthUrl());
            // baseUrl should be something like "https://sandbox.orcid.org/" or "https://orcid.org/"
            baseUrl = orcidAuthUrl.getProtocol() + "://" + orcidAuthUrl.getHost() + "/";
            // baseApiUrl should result in something like "https://api.sandbox.orcid.org/v3.0/" or "https://api.orcid.org/v3.0/"
            baseApiUrl = orcidAuthUrl.getProtocol() + "://api." + orcidAuthUrl.getHost() + "/v3.0/";
        } catch (MalformedURLException e) {
            LOG.error("The ORCID Auth URL in the dropwizard configuration file is malformed.", e);
        }

        orcidClientId = configuration.getOrcidClientID();
        orcidClientSecret = configuration.getOrcidClientSecret();
    }

    public static String getOrcidBaseApiUrl() {
        return baseApiUrl;
    }

    /**
     * Get a read-public access token for reading public information.
     * https://info.orcid.org/documentation/api-tutorials/api-tutorial-read-data-on-a-record/#Get_an_access_token
     * @return An access token
     */
    public static Optional<String> getOrcidAccessToken() {
        String requestData = String.format("grant_type=client_credentials&scope=/read-public&client_id=%s&client_secret=%s", orcidClientId, orcidClientSecret);
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseUrl + "oauth/token"))
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .headers(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .POST(ofString(requestData)).build();

            HttpResponse<String> response = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.SC_OK) {
                LOG.error("Could not get ORCID access token: {}", response.body());
                return Optional.empty();
            }

            Map<String, String> responseMap = MAPPER.readValue(response.body(), Map.class);
            return Optional.of(responseMap.get("access_token"));
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            LOG.error("Could not get ORCID access token", ex);
            return Optional.empty();
        }
    }

    /**
     * Construct the XML for an ORCID work so that it can be posted using the ORCID API
     * Current populated fields are Title, Subtitle, Last Modified, CreatedDate, DOI URL, DOI value, Short description
     * External ID value must be unique, everything else can be the same (title, subtitle, etc)
     * An entry (and an optional version) in Dockstore can sent to ORCID
     * A DOI for the entry or version is required
     * TODO: The implementation of short description needs work to ensure it's not too long
     * @param e The entry to be sent to ORCID
     * @param optionalVersion   Optional version of the entry to send to ORCID
     * @return An ORCID Work to be sent to ORCID
     * @throws JAXBException
     * @throws DatatypeConfigurationException
     */
    public static String getOrcidWorkString(Entry e, Optional<Version> optionalVersion, String putCode) throws JAXBException, DatatypeConfigurationException {
        // Length of the work description to send to ORCID. Arbitrarily set to 4x tweet length.
        final int descriptionLength = 4 * 280;
        Work work = new Work();
        WorkTitle workTitle = new WorkTitle();
        Title title = new Title();
        ExternalIDs externalIDs = new ExternalIDs();
        ExternalID externalID = new ExternalID();
        externalID.setType("doi");
        if (optionalVersion.isPresent()) {
            Version v = optionalVersion.get();
            String doi = v.getDoiURL(); // getDoiURL returns a DOI, not a URL
            externalID.setUrl(new Url(doiToUrl(doi)));
            externalID.setValue(doi);
            title.setContent(e.getEntryPath() + ":" + v.getName());
            work.setShortDescription(StringUtils.abbreviate(v.getDescription(), descriptionLength));
        } else {
            String doi = e.getConceptDoi();
            externalID.setUrl(new Url(doiToUrl(doi)));
            externalID.setValue(doi);
            title.setContent(e.getEntryPath());
            work.setShortDescription(StringUtils.abbreviate(e.getDescription(), descriptionLength));
        }
        Title journalTitle = new Title();
        journalTitle.setContent("Dockstore");
        work.setJournalTitle(journalTitle);
        if (putCode != null) {
            work.setPutCode(Long.valueOf(putCode));
        }
        workTitle.setTitle(title);
        work.setWorkTitle(workTitle);
        work.setWorkType(WorkType.SOFTWARE);
        externalID.setRelationship(Relationship.SELF);
        externalIDs.getExternalIdentifier().add(externalID);
        work.setWorkExternalIdentifiers(externalIDs);
        GregorianCalendar gregory = new GregorianCalendar();
        gregory.setTime(new Date());
        XMLGregorianCalendar calendar = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(
                        gregory);
        CreatedDate createdDate = new CreatedDate(calendar);
        work.setCreatedDate(createdDate);
        LastModifiedDate lastModifiedDate = new LastModifiedDate();
        lastModifiedDate.setValue(calendar);
        work.setLastModifiedDate(lastModifiedDate);
        return transformWork(work);
    }

    public static String doiToUrl(String doi) {
        // If the DOI appears well-formed, return the corresponding doi.org proxy URL:
        // https://www.doi.org/factsheets/DOIProxy.html
        // A well-formed DOI starts with "10." and contains a slash:
        // https://www.doi.org/doi_handbook/2_Numbering.html#2.2
        if (doi != null && doi.startsWith("10.") && doi.contains("/")) {
            return "https://doi.org/" + doi;
        }
        // If the argument isn't a well-formed DOI, it might be a URL already, or empty.  Return as-is.
        return doi;
    }

    public static HttpResponse<String> postWorkString(String id, String workString, String token)
            throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id + "/work")).header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE).header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).POST(ofString(workString)).build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * This updates an existing ORCID work
     * @return
     */
    public static HttpResponse<String> putWorkString(String id, String workString, String token, String putCode)
            throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id + "/work/" + putCode)).header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE).header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).PUT(ofString(workString)).build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }


    /**
     * Get the ORCID put code from the response
     *
     * @param httpResponse
     * @return
     */
    public static String getPutCodeFromLocation(HttpResponse<String> httpResponse) {
        Optional<String> location = httpResponse.headers().firstValue("Location");
        if (location.isEmpty()) {
            throw new CustomWebApplicationException("Could not get ORCID work put code", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        URI uri;
        try {
            uri = new URI(location.get());
        } catch (URISyntaxException e) {
            throw new CustomWebApplicationException("Could not get ORCID work put code", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String transformWork(Work work) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Work.class);
        StringWriter writer = new StringWriter();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(work, writer);
        return writer.getBuffer().toString();
    }

    public static Optional<Long> searchForPutCodeByDoiUrl(String id, List<Token> orcidTokens, String doiUrl)
            throws IOException, URISyntaxException, InterruptedException, JAXBException {
        // Get user's ORCID works
        HttpResponse<String> response = getAllWorks(id, orcidTokens.get(0).getToken());

        if (response.statusCode() == HttpStatus.SC_OK) {
            Works works = transformXmlToWorks(response.body());
            // Find the ORCID work with the DOI URL and get its put code
            for (WorkGroup work : works.getWorkGroup()) {
                // There should only be one external ID that matches the DOI url
                Optional<ExternalID> doiExternalId = work.getIdentifiers().getExternalIdentifier().stream().filter(externalID -> externalID.getValue().equals(doiUrl)).findFirst();
                if (doiExternalId.isPresent()) {
                    WorkSummary workSummary = work.getWorkSummary().get(0);
                    return Optional.of(workSummary.getPutCode());
                }
            }
            return Optional.empty();
        } else {
            throw new CustomWebApplicationException("Could not get all ORCID works to find put code for the existing ORCID work: " + response.body(), response.statusCode());
        }
    }

    /**
     * This gets all works belonging to the orcid author with the provided orcid ID.
     */
    public static HttpResponse<String> getAllWorks(String id, String token) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id + "/works")).header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE).header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).GET().build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Transforms the ORCID XML response from a get all works call to a Works object. Assumes that the XML from Orcid is safe.
     */
    private static Works transformXmlToWorks(String worksXml) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Works.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (Works) unmarshaller.unmarshal(new StringReader(worksXml));
    }

    /**
     * Creates an OrcidAuthor, retrieving information about the author using the ORCID API.
     * Throws an exception if the person with the ORCID ID does not exist on the ORCID site.
     * @param id ORCID ID of the ORCID author
     * @param token ORCID token
     * @return OrcidAuthor object
     */
    public static OrcidAuthor createOrcidAuthor(String id, String token)
            throws URISyntaxException, IOException, InterruptedException, JAXBException, CustomWebApplicationException {
        OrcidAuthor orcidAuthor = new OrcidAuthor(id);

        HttpResponse<String> response = getPerson(id, token);
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOG.error("Could not get ORCID person with ID {}: {}", id, response.body());
            throw new CustomWebApplicationException("Could not get ORCID person with ID " + id, HttpStatus.SC_BAD_REQUEST);
        }

        Person person = transformXmlToPerson(response.body());
        // Set name
        String fullName = person.getName().getGivenNames().getContent(); // At a minimum, the Orcid Author will have a first name because it's mandated by ORCID
        if (person.getName().getFamilyName() != null) {
            fullName += " " + person.getName().getFamilyName().getContent();
        }
        orcidAuthor.setName(fullName);
        // Set email
        Optional<Email> primaryEmail = person.getEmails().getEmails().stream().filter(Email::isPrimary).findFirst();
        if (primaryEmail.isPresent()) {
            orcidAuthor.setEmail(primaryEmail.get().getEmail());
        }

        response = getAllEmployments(id, token);
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOG.error("Could not get employment details for ORCID user with ID {}: {}", id, response.body());
            return orcidAuthor; // Can still return the author since the author's name is set at this point
        }

        Employments employments = transformXmlToEmployments(response.body());
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
     * Gets details of the person identified by the given ORCID ID.
     * @param id ORCID ID of the person to get details for
     * @param token ORCID token
     * @return HttpResponse
     */
    public static HttpResponse<String> getPerson(String id, String token) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id + "/person"))
                .header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE)
                .header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).GET().build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Transforms the ORCID XML response from a get person call to a Person object. Assumes that the XML from ORCID is safe.
     * @param personXml
     * @return Person object
     * @throws JAXBException
     */
    static Person transformXmlToPerson(String personXml) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Person.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (Person) unmarshaller.unmarshal(new StringReader(personXml));
    }

    /**
     * Gets all employments of the person identified by the given ORCID ID.
     * @param id ORCID ID of the person to get employments for
     * @param token ORCID token
     * @return HttpResponse
     */
    public static HttpResponse<String> getAllEmployments(String id, String token) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id + "/employments"))
                .header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE)
                .header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).GET().build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Transforms the ORCID XML response from a get all employments call to an Employments object. Assumes that the XML from ORCID is safe.
     * @param employmentsXml
     * @return Employments object
     * @throws JAXBException
     */
    static Employments transformXmlToEmployments(String employmentsXml) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Employments.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (Employments) unmarshaller.unmarshal(new StringReader(employmentsXml));
    }
}
