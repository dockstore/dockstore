package io.openapi.api.impl;

import java.util.Date;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dockstore.webservice.core.User;
import io.openapi.api.ServiceInfoApiService;
import io.openapi.model.Service;
import io.openapi.model.ServiceType;

public class ServiceInfoApiServiceImpl extends ServiceInfoApiService {
    @Override
    public Response getServiceInfo(SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        Service service = getService();
        return Response.ok().entity(service).build();
    }

    public static Service getService() {
        Service service = new Service();
        service.setId("1");
        service.setName("Dockstore");
        service.setType(getServiceType());
        service.setDescription("The GA4GH Tool Registry API is a standard for listing and describing available tools (both stand-alone, Docker-based tools as well as workflows in CWL, WDL, Nextflow, or Galaxy) in a given registry,");
        service.setOrganization(getOrganization());
        service.setContactUrl("https://discuss.dockstore.org/t/opening-helpdesk-tickets/1506");
        service.setDocumentationUrl("https://docs.dockstore.org/en/develop");
        // TODO: Somehow get swagger codegen to generate string parameter (RFC 3339) instead of Date
        service.setCreatedAt(new Date());
        // TODO: Somehow get swagger codegen to generate string parameter (RFC 3339) instead of Date
        service.setUpdatedAt(new Date());
        service.setEnvironment("prod");
        service.setVersion("2.0.1");
        return service;
    }

    private static ServiceType getServiceType() {
        ServiceType serviceType = new ServiceType();
        serviceType.setGroup("org.ga4gh");
        serviceType.setArtifact("TRS");
        serviceType.setVersion("2.0.1");
        return serviceType;
    }

    private static Organization getOrganization() {
        Organization organization = new Organization();
        organization.setName("Dockstore");
        organization.setUrl("https://dockstore.org");
        return organization;

    }

    // TODO: Figure out why Swagger codegen couldn't auto-generate an anonymous class (maybe it's just not possible?)
    static class Organization {
        String name;
        String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

}
