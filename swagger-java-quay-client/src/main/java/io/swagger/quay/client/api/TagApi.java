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

import io.swagger.quay.client.model.MoveTag;
import io.swagger.quay.client.model.RevertTag;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class TagApi {
  private ApiClient apiClient;

  public TagApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TagApi(ApiClient apiClient) {
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
   * 
   * @param repository The full path of the repository. e.g. namespace/name
   * @param page Page index for the results. Default 1.
   * @param limit Limit to the number of results to return per page. Max 100.
   * @param specificTag Filters the tags to the specific tag.
   * @return void
   */
  public void listRepoTags (String repository, Integer page, Integer limit, String specificTag) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling listRepoTags");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/tag/".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "page", page));
    
    queryParams.addAll(apiClient.parameterToPairs("", "limit", limit));
    
    queryParams.addAll(apiClient.parameterToPairs("", "specificTag", specificTag));
    

    

    

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
   * Change which image a tag points to or create a new tag.
   * @param tag The name of the tag
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void changeTagImage (String tag, String repository, MoveTag body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'tag' is set
    if (tag == null) {
      throw new ApiException(400, "Missing the required parameter 'tag' when calling changeTagImage");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling changeTagImage");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling changeTagImage");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/tag/{tag}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tag" + "\\}", apiClient.escapeString(tag.toString()))
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

    
    apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Delete the specified repository tag.
   * @param tag The name of the tag
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void deleteFullTag (String tag, String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'tag' is set
    if (tag == null) {
      throw new ApiException(400, "Missing the required parameter 'tag' when calling deleteFullTag");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling deleteFullTag");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/tag/{tag}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tag" + "\\}", apiClient.escapeString(tag.toString()))
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
   * List the images for the specified repository tag.
   * @param tag The name of the tag
   * @param repository The full path of the repository. e.g. namespace/name
   * @param owned If specified, only images wholely owned by this tag are returned.
   * @return void
   */
  public void listTagImages (String tag, String repository, Boolean owned) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'tag' is set
    if (tag == null) {
      throw new ApiException(400, "Missing the required parameter 'tag' when calling listTagImages");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling listTagImages");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/tag/{tag}/images".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tag" + "\\}", apiClient.escapeString(tag.toString()))
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "owned", owned));
    

    

    

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
   * Reverts a repository tag back to a previous image in the repository.
   * @param tag The name of the tag
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void revertTag (String tag, String repository, RevertTag body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'tag' is set
    if (tag == null) {
      throw new ApiException(400, "Missing the required parameter 'tag' when calling revertTag");
    }
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling revertTag");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling revertTag");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/tag/{tag}/revert".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tag" + "\\}", apiClient.escapeString(tag.toString()))
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
