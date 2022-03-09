package io.dockstore.webservice.helpers;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static java.net.http.HttpRequest.BodyPublishers.ofString;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.OrcidAuthorInformation;
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
import java.util.regex.Pattern;
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
import org.orcid.jaxb.model.v3.release.common.Visibility;
import org.orcid.jaxb.model.v3.release.record.Email;
import org.orcid.jaxb.model.v3.release.record.ExternalID;
import org.orcid.jaxb.model.v3.release.record.ExternalIDs;
import org.orcid.jaxb.model.v3.release.record.Name;
import org.orcid.jaxb.model.v3.release.record.Record;
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
    private static final Pattern ORCID_ID_PATTERN = Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}"); // ex: 1234-1234-1234-1234
    private static final Class[] JAXB_CONTEXT_CLASSES = {Work.class, Works.class, Record.class};

    private static String baseApiUrl; // baseApiUrl should result in something like "https://api.sandbox.orcid.org/v3.0/" or "https://api.orcid.org/v3.0/"
    private static String baseUrl; // baseUrl should be something like "https://sandbox.orcid.org/" or "https://orcid.org/"
    private static String orcidClientId;
    private static String orcidClientSecret;
    private static volatile JAXBContext jaxbContext;
    private static volatile String readPublicAccessToken;

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
            throw new CustomWebApplicationException("The ORCID Auth URL in the dropwizard configuration file is malformed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        orcidClientId = configuration.getOrcidClientID();
        orcidClientSecret = configuration.getOrcidClientSecret();
    }

    public static String getOrcidBaseApiUrl() {
        return baseApiUrl;
    }

    private static JAXBContext getJaxbContext() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(JAXB_CONTEXT_CLASSES);
        }
        return jaxbContext;
    }

    /**
     * Get a read-public access token for reading public information.
     * https://info.orcid.org/documentation/api-tutorials/api-tutorial-read-data-on-a-record/#Get_an_access_token
     * @return An access token
     */
    public static Optional<String> getOrcidAccessToken() {
        if (readPublicAccessToken == null) {
            String requestData = String.format("grant_type=client_credentials&scope=/read-public&client_id=%s&client_secret=%s",
                    orcidClientId, orcidClientSecret);
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseUrl + "oauth/token"))
                        .header(HttpHeaders.ACCEPT, "application/json").headers(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded").POST(ofString(requestData)).build();

                HttpResponse<String> response = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != HttpStatus.SC_OK) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Could not get ORCID access token: {}", response.body());
                    }
                    return Optional.empty();
                }

                Map<String, String> responseMap = MAPPER.readValue(response.body(), Map.class);
                readPublicAccessToken = responseMap.get("access_token");
            } catch (URISyntaxException | IOException ex) {
                LOG.error("Could not get ORCID access token", ex);
                return Optional.empty();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOG.error("Could not get ORCID access token", ex);
                return Optional.empty();
            }
        }

        return Optional.of(readPublicAccessToken);
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
        JAXBContext context = getJaxbContext();
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
        JAXBContext context = getJaxbContext();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (Works) unmarshaller.unmarshal(new StringReader(worksXml));
    }

    /**
     * Retrieves public information about the ORCID author using the ORCID API.
     * Throws an exception if the person with the ORCID ID does not exist on the ORCID site.
     * @param id ORCID ID of the ORCID author
     * @param token ORCID token
     * @return OrcidAuthorInfo object
     */
    public static Optional<OrcidAuthorInformation> getOrcidAuthorInformation(String id, String token) {
        OrcidAuthorInformation orcidAuthorInfo = new OrcidAuthorInformation(id);

        try {
            HttpResponse<String> response = getRecordDetails(id, token);
            if (response.statusCode() != HttpStatus.SC_OK) {
                if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
                    LOG.error("ORCID iD {} not found", id);
                } else {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Could not get ORCID record with iD {}: {}", id, response.body());
                    }
                }
                return Optional.empty();
            }

            Record orcidRecord = transformXmlToRecord(response.body());
            // Deprecated and deactivated records don't have data
            // Info about deprecated record: https://support.orcid.org/hc/en-us/articles/360006896634-Removing-your-additional-or-duplicate-ORCID-iD
            // Info about deactivated record: https://support.orcid.org/hc/en-us/articles/360006973813-Deactivating-an-ORCID-account
            if (orcidRecord.getDeprecated() != null || orcidRecord.getHistory().getDeactivationDate() != null) {
                return Optional.empty();
            }

            // Set name
            Name name = orcidRecord.getPerson().getName();
            if (name != null && name.getVisibility() == Visibility.PUBLIC) {
                String fullName = name.getGivenNames().getContent(); // At a minimum, the Orcid Author will have a first name because it's mandated by ORCID

                if (name.getFamilyName() != null) {
                    fullName += " " + name.getFamilyName().getContent();
                }
                orcidAuthorInfo.setName(fullName);
            }

            // Set email
            Optional<Email> primaryEmail = orcidRecord.getPerson().getEmails().getEmails().stream().filter(Email::isPrimary).findFirst();
            if (primaryEmail.isPresent() && primaryEmail.get().getVisibility() == Visibility.PUBLIC) {
                orcidAuthorInfo.setEmail(primaryEmail.get().getEmail());
            }

            Employments employments = orcidRecord.getActivitiesSummary().getEmployments();
            Collection<AffiliationGroup<EmploymentSummary>> affiliationGroups = employments.getEmploymentGroups();
            if (affiliationGroups.iterator().hasNext()) {
                // Set affiliation and role
                EmploymentSummary employmentSummary = affiliationGroups.iterator().next().getActivities().get(0); // The first employment in the list is the most recent according to end date
                if (employmentSummary.getVisibility() == Visibility.PUBLIC) {
                    orcidAuthorInfo.setAffiliation(employmentSummary.getOrganization().getName());
                    orcidAuthorInfo.setRole(employmentSummary.getRoleTitle());
                }
            }
            return Optional.of(orcidAuthorInfo);
        } catch (URISyntaxException | IOException | JAXBException e) {
            LOG.error("Could not get ORCID author information", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            LOG.error("Could not get ORCID author information", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Get ORCID record details for record with the given ORCID iD.
     * @param id ORCID iD
     * @param token ORCID token
     * @return HttpResponse
     */
    public static HttpResponse<String> getRecordDetails(String id, String token) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseApiUrl + id))
                .header(HttpHeaders.CONTENT_TYPE, ORCID_XML_CONTENT_TYPE)
                .header(HttpHeaders.AUTHORIZATION, JWT_SECURITY_DEFINITION_NAME + " " + token).GET().build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Transforms the ORCID XML response from a get record details call to a Record object. Assumes that the XML from ORCID is safe.
     * @param recordXml ORCID Record XML response
     * @return Record Object
     */
    public static Record transformXmlToRecord(String recordXml) throws JAXBException {
        JAXBContext context = getJaxbContext();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (Record) unmarshaller.unmarshal(new StringReader(recordXml));
    }

    public static boolean isValidOrcidId(String orcidId) {
        return ORCID_ID_PATTERN.matcher(orcidId).matches();
    }
}
