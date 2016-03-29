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

package io.swagger.quay.client.api;

import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.Pair;
import io.swagger.quay.client.TypeRef;

import io.swagger.quay.client.model.BuildTriggerActivateRequest;
import io.swagger.quay.client.model.RunParameters;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class TriggerApi {
  private ApiClient apiClient;

  public TriggerApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TriggerApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * 
   * List the triggers for the specified repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void listBuildTriggers (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling listBuildTriggers");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Get information for the specified build trigger.
   * @param triggerUuid The UUID of the build trigger
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void getBuildTrigger (String triggerUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'triggerUuid' is set
    if (triggerUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'triggerUuid' when calling getBuildTrigger");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getBuildTrigger");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/{trigger_uuid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "trigger_uuid" + "\\}", apiClient.escapeString(triggerUuid.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Delete the specified build trigger.
   * @param triggerUuid The UUID of the build trigger
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void deleteBuildTrigger (String triggerUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'triggerUuid' is set
    if (triggerUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'triggerUuid' when calling deleteBuildTrigger");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling deleteBuildTrigger");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/{trigger_uuid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "trigger_uuid" + "\\}", apiClient.escapeString(triggerUuid.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "DELETE", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Activate the specified build trigger.
   * @param triggerUuid The UUID of the build trigger
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void activateBuildTrigger (String triggerUuid, String repository, BuildTriggerActivateRequest body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'triggerUuid' is set
    if (triggerUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'triggerUuid' when calling activateBuildTrigger");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling activateBuildTrigger");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling activateBuildTrigger");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/{trigger_uuid}/activate".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "trigger_uuid" + "\\}", apiClient.escapeString(triggerUuid.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * List the builds started by the specified trigger.
   * @param triggerUuid The UUID of the build trigger
   * @param repository The full path of the repository. e.g. namespace/name
   * @param limit The maximum number of builds to return
   * @return void
   */
  public void listTriggerRecentBuilds (String triggerUuid, String repository, Integer limit) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'triggerUuid' is set
    if (triggerUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'triggerUuid' when calling listTriggerRecentBuilds");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling listTriggerRecentBuilds");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/{trigger_uuid}/builds".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "trigger_uuid" + "\\}", apiClient.escapeString(triggerUuid.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "limit", limit));
    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Manually start a build from the specified trigger.
   * @param triggerUuid The UUID of the build trigger
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void manuallyStartBuildTrigger (String triggerUuid, String repository, RunParameters body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'triggerUuid' is set
    if (triggerUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'triggerUuid' when calling manuallyStartBuildTrigger");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling manuallyStartBuildTrigger");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling manuallyStartBuildTrigger");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/trigger/{trigger_uuid}/start".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "trigger_uuid" + "\\}", apiClient.escapeString(triggerUuid.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
}
