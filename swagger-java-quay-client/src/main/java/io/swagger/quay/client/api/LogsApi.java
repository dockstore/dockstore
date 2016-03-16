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


import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class LogsApi {
  private ApiClient apiClient;

  public LogsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public LogsApi(ApiClient apiClient) {
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
   * Gets the aggregated logs for the specified organization.
   * @param orgname The name of the organization
   * @param performer Username for which to filter logs.
   * @param endtime Latest time to which to get logs. (%m/%d/%Y %Z)
   * @param starttime Earliest time from which to get logs. (%m/%d/%Y %Z)
   * @return void
   */
  public void getAggregateOrgLogs (String orgname, String performer, String endtime, String starttime) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getAggregateOrgLogs");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/aggregatelogs".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "performer", performer));
    
    queryParams.addAll(apiClient.parameterToPairs("", "endtime", endtime));
    
    queryParams.addAll(apiClient.parameterToPairs("", "starttime", starttime));
    

    

    

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
   * List the logs for the specified organization.
   * @param orgname The name of the organization
   * @param page The page number for the logs
   * @param performer Username for which to filter logs.
   * @param endtime Latest time to which to get logs. (%m/%d/%Y %Z)
   * @param starttime Earliest time from which to get logs. (%m/%d/%Y %Z)
   * @return void
   */
  public void listOrgLogs (String orgname, Integer page, String performer, String endtime, String starttime) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling listOrgLogs");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/logs".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "page", page));
    
    queryParams.addAll(apiClient.parameterToPairs("", "performer", performer));
    
    queryParams.addAll(apiClient.parameterToPairs("", "endtime", endtime));
    
    queryParams.addAll(apiClient.parameterToPairs("", "starttime", starttime));
    

    

    

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
   * Returns the aggregated logs for the specified repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param endtime Latest time to which to get logs (%m/%d/%Y %Z)
   * @param starttime Earliest time from which to get logs (%m/%d/%Y %Z)
   * @return void
   */
  public void getAggregateRepoLogs (String repository, String endtime, String starttime) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getAggregateRepoLogs");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/aggregatelogs".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "endtime", endtime));
    
    queryParams.addAll(apiClient.parameterToPairs("", "starttime", starttime));
    

    

    

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
   * List the logs for the specified repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param page The page number for the logs
   * @param endtime Latest time to which to get logs (%m/%d/%Y %Z)
   * @param starttime Earliest time from which to get logs (%m/%d/%Y %Z)
   * @return void
   */
  public void listRepoLogs (String repository, Integer page, String endtime, String starttime) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling listRepoLogs");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/logs".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "page", page));
    
    queryParams.addAll(apiClient.parameterToPairs("", "endtime", endtime));
    
    queryParams.addAll(apiClient.parameterToPairs("", "starttime", starttime));
    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
}
