/*
 *    Copyright 2016 OICR
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
