package io.swagger.api.factories;

import io.swagger.api.ToolsApiService;
import io.swagger.api.impl.ToolsApiServiceImpl;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-29T22:00:17.650Z")
public class ToolsApiServiceFactory {

   private final static ToolsApiService service = new ToolsApiServiceImpl();

   public static ToolsApiService getToolsApi()
   {
      return service;
   }
}
