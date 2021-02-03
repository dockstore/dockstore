package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.orcid.jaxb.model.common.Relationship;
import org.orcid.jaxb.model.common.WorkType;

import org.orcid.jaxb.model.v3.release.common.*;
import org.orcid.jaxb.model.v3.release.record.ExternalID;
import org.orcid.jaxb.model.v3.release.record.ExternalIDs;
import org.orcid.jaxb.model.v3.release.record.Work;
import org.orcid.jaxb.model.v3.release.record.WorkTitle;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Optional;

public class ORCIDHelper {
    /**
     * Construct the XML for an ORCID work so that it can be posted using the ORCID API
     * Current populated fields are Title, Subtitle, Last Modified, CreatedDate, DOI link, Short description
     * An entry (and an optional version) in Dockstore can sent to ORCID
     * @param e The entry to be sent to ORCID
     * @param optionalVersion   Optional version of the entry to send to ORCID
     * @return An ORCID Work to be sent to ORCID
     * @throws JAXBException
     * @throws DatatypeConfigurationException
     */
    public static String getOrcidWorkString(Entry e, Optional<Version> optionalVersion) throws JAXBException, DatatypeConfigurationException {
        Work work = new Work();
        WorkTitle workTitle = new WorkTitle();
        Title title = new Title();
        Subtitle subtitle = new Subtitle("Dockstore Workf");
        ExternalIDs externalIDs = new ExternalIDs();
        ExternalID externalID = new ExternalID();
        externalID.setType("doi");
        externalID.setValue("potato");
        if (optionalVersion.isPresent()) {
            Version v = optionalVersion.get();
            Url url = new Url(v.getDoiURL());
            externalID.setUrl(url);
            title.setContent(e.getEntryPath() + ":" + v.getName());
        } else {
            Url url = new Url(e.getConceptDoi());
            externalID.setUrl(url);
            title.setContent(e.getEntryPath());
        }
        workTitle.setTitle(title);
        workTitle.setSubtitle(subtitle);
        work.setWorkTitle(workTitle);
        work.setShortDescription("A workflow exported from Dockstore");
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

    static String transformWork(Work work) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Work.class);
        StringWriter writer = new StringWriter();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(work, writer);
        return writer.getBuffer().toString();
    }

    public static HttpResponse postWorkString(String id, String workString, String token) throws IOException {
        HttpPost postRequest = new HttpPost("https://api.sandbox.orcid.org/v3.0/" + id + "/work");
        postRequest.addHeader("content-type", "application/vnd.orcid+xml");
        postRequest.addHeader("Authorization", "Bearer " + token);
        StringEntity workEntity = new StringEntity(workString);
        postRequest.setEntity(workEntity);
        CloseableHttpClient build = HttpClientBuilder.create().build();
        HttpResponse response = build.execute(postRequest);
        return response;
    }
}
