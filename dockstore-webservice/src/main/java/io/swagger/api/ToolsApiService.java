package io.swagger.api;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-26T18:50:10.120Z")
public abstract class ToolsApiService {
  
      public abstract Response toolsGet(String registryId,String registry,String organization,String name,String toolname,String description,String author,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdGet(String registryId,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdDescriptorGet(String registryId,String format,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsRegistryIdDockerfileGet(String registryId,SecurityContext securityContext)
      throws NotFoundException;
  
}
