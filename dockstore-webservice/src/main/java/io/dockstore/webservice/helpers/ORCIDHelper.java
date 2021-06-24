package io.dockstore.webservice.helpers;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;
import java.io.IOException;
import java.io.StringWriter;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
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
import org.orcid.jaxb.model.v3.release.record.ExternalID;
import org.orcid.jaxb.model.v3.release.record.ExternalIDs;
import org.orcid.jaxb.model.v3.release.record.Work;
import org.orcid.jaxb.model.v3.release.record.WorkTitle;

// Swagger-ui available here: https://api.orcid.org/v3.0/#!/Development_Member_API_v3.0/
public final class ORCIDHelper {
    private ORCIDHelper() {
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
            Url url = new Url(v.getDoiURL());
            externalID.setUrl(url);
            externalID.setValue(v.getDoiURL());
            title.setContent(e.getEntryPath() + ":" + v.getName());
            work.setShortDescription(StringUtils.abbreviate(v.getDescription(), descriptionLength));
        } else {
            Url url = new Url(e.getConceptDoi());
            externalID.setUrl(url);
            externalID.setValue(e.getConceptDoi());
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

    public static HttpResponse<String> postWorkString(String baseURL, String id, String workString, String token)
            throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseURL + id + "/work")).header(HttpHeaders.CONTENT_TYPE, "application/vnd.orcid+xml").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).POST(ofString(workString)).build();
        return HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * This updates an existing ORCID work
     * @return
     */
    public static HttpResponse<String> putWorkString(String baseURL, String id, String workString, String token, String putCode)
            throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(baseURL + id + "/work/" + putCode)).header(HttpHeaders.CONTENT_TYPE, "application/vnd.orcid+xml").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).PUT(ofString(workString)).build();
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
}
