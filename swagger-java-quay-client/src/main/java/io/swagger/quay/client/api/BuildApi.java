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

import io.swagger.quay.client.model.RepositoryBuildRequest;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class BuildApi {
  private ApiClient apiClient;

  public BuildApi() {
    this(Configuration.getDefaultApiClient());
  }

  public BuildApi(ApiClient apiClient) {
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
   * Get the list of repository builds.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param since Returns all builds since the given unix timecode
   * @param limit The maximum number of builds to return
   * @return void
   */
  public void getRepoBuilds (String repository, Integer since, Integer limit) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoBuilds");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "since", since));
    
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
   * Request that a repository be built and pushed from the specified input.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void requestRepoBuild (String repository, RepositoryBuildRequest body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling requestRepoBuild");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling requestRepoBuild");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/".replaceAll("\\{format\\}","json")
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
   * Returns information about a build.
   * @param buildUuid The UUID of the build
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void getRepoBuild (String buildUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'buildUuid' is set
    if (buildUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'buildUuid' when calling getRepoBuild");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoBuild");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/{build_uuid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "build_uuid" + "\\}", apiClient.escapeString(buildUuid.toString()))
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
   * Cancels a repository build if it has not yet been picked up by a build worker.
   * @param buildUuid The UUID of the build
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void cancelRepoBuild (String buildUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'buildUuid' is set
    if (buildUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'buildUuid' when calling cancelRepoBuild");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling cancelRepoBuild");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/{build_uuid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "build_uuid" + "\\}", apiClient.escapeString(buildUuid.toString()))
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
   * Return the build logs for the build specified by the build uuid.
   * @param buildUuid The UUID of the build
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void getRepoBuildLogs (String buildUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'buildUuid' is set
    if (buildUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'buildUuid' when calling getRepoBuildLogs");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoBuildLogs");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/{build_uuid}/logs".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "build_uuid" + "\\}", apiClient.escapeString(buildUuid.toString()))
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
   * Return the status for the builds specified by the build uuids.
   * @param buildUuid The UUID of the build
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void getRepoBuildStatus (String buildUuid, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'buildUuid' is set
    if (buildUuid == null) {
      throw new ApiException(400, "Missing the required parameter 'buildUuid' when calling getRepoBuildStatus");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoBuildStatus");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/build/{build_uuid}/status".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "build_uuid" + "\\}", apiClient.escapeString(buildUuid.toString()))
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
  
}
