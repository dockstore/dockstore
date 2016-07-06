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

package io.swagger.api.factories;

import io.swagger.api.ToolTypesApiService;
import io.swagger.api.impl.ToolTypesApiServiceImpl;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-07-05T15:18:23.446Z")
public class ToolTypesApiServiceFactory {

   private final static ToolTypesApiService service = new ToolTypesApiServiceImpl();

   public static ToolTypesApiService getToolTypesApi()
   {
      return service;
   }
}
