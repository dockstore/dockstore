package io.swagger.api;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-29T22:00:17.650Z")
public abstract class ToolsApiService {
  
      public abstract Response toolsGet(String registryId,String registry,String organization,String name,String toolname,String description,String author,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsMetadataGet(SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdGet(String registryId,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdVersionVersionIdGet(String registryId,String versionId,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdVersionVersionIdDescriptorGet(String registryId,String versionId,String format,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdVersionVersionIdDockerfileGet(String registryId,String versionId,SecurityContext securityContext)
      throws NotFoundException;
  
}
