package io.swagger.api;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-22T21:28:57.577Z")
public abstract class ToolsApiService {
  
      public abstract Response toolsGet(String id,String registry,String organization,String name,String toolname,String description,String author,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsQueryGet(String query,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsIdDescriptorGet(String id,String format,SecurityContext securityContext)
      throws NotFoundException;
  
      public abstract Response toolsIdDockerfileGet(String id,SecurityContext securityContext)
      throws NotFoundException;
  
}
