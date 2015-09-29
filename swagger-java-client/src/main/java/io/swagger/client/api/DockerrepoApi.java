package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.*;

import java.util.*;

import io.swagger.client.model.ARegisteredContainerThatAUserHasSubmitted;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-09-29T10:53:03.112-04:00")
public class DockerrepoApi {
  private ApiClient apiClient;

  public DockerrepoApi() {
    this(Configuration.getDefaultApiClient());
  }

  public DockerrepoApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * List all repos known via all registered tokens
   * List docker container repos currently known. Right now, tokens are used to synchronously talk to the quay.io API to list repos. Ultimately, we should cache this information and refresh either by user request or by time TODO: This should be a properly defined list of objects, it also needs admin authentication
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted getRepos () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all registered containers
   * 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted getAllRegisteredContainers () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/getAllRegisteredContainers".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the list of repository builds.
   * For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io
   * @param repository 
   * @param userId 
   * @return String
   */
  public String getBuilds (String repository, Long userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/getBuilds".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "repository", repository));
    
    queryParams.addAll(apiClient.parameterToPairs("", "userId", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<String>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the corresponding collab.json and/or cwl file on Github
   * Enter full path of container (add quay.io if using quay.io)
   * @param repository 
   * @return String
   */
  public String getCollabFile (String repository) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/getCollabFile".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "repository", repository));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<String>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a registered container
   * Lists info of container. Enter full path (include quay.io in path)
   * @param userId 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted getRegisteredContainer (String userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/getRegisteredContainer".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "user_id", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Fetch repo from quay.io
   * 
   * @param repository The full path of the repository. e.g. namespace/name
   * @param userId user id
   * @return String
   */
  public String getRepo (String repository, Long userId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepo");
    }
    
    // verify the required parameter 'userId' is set
    if (userId == null) {
      throw new ApiException(400, "Missing the required parameter 'userId' when calling getRepo");
    }
    
    // create path and map variables
    String path = "/docker.repo/getRepo/{userId}/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()))
      .replaceAll("\\{" + "userId" + "\\}", apiClient.escapeString(userId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<String>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all registered containers from a user
   * Get user&#39;s registered containers only
   * @param userId 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted getUserRegisteredContainers (Long userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/getUserRegisteredContainers".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "user_id", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List repos owned by the logged-in user
   * Lists all registered and unregistered containers owned by the user
   * @param enduserId 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted listOwned (Long enduserId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/listOwned".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "enduser_id", enduserId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh repos owned by the logged-in user
   * Updates some metadata
   * @param userId 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted refreshRepos (Long userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/refreshRepos".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "user_id", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Register a container
   * Register a container (public or private). Assumes that user is using quay.io and github. Include quay.io in path if using quay.io
   * @param repository 
   * @param enduserId 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted registerContainer (String repository, Long enduserId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/registerContainer".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "repository", repository));
    
    queryParams.addAll(apiClient.parameterToPairs("", "enduser_id", enduserId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Search for matching registered containers
   * Search on the name (full path name) and description.
   * @param pattern 
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted searchContainers (String pattern) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/searchContainers".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "pattern", pattern));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * User shares a container with a chosen group
   * Needs to be fleshed out.
   * @param containerId 
   * @param groupId 
   * @return void
   */
  public void shareWithGroup (Long containerId, Long groupId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/shareWithGroup".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "container_id", containerId));
    
    queryParams.addAll(apiClient.parameterToPairs("", "group_id", groupId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * User shares a container with a chosen user
   * Needs to be fleshed out.
   * @param containerId 
   * @param userId 
   * @return void
   */
  public void shareWithUser (Long containerId, Long userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/docker.repo/shareWithUser".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "container_id", containerId));
    
    queryParams.addAll(apiClient.parameterToPairs("", "user_id", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * Deletes a container
   * 
   * @param containerId Container id to delete
   * @return ARegisteredContainerThatAUserHasSubmitted
   */
  public ARegisteredContainerThatAUserHasSubmitted unregisterContainer (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling unregisterContainer");
    }
    
    // create path and map variables
    String path = "/docker.repo/unregisterContainer/{containerId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "containerId" + "\\}", apiClient.escapeString(containerId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ARegisteredContainerThatAUserHasSubmitted>() {};
    return apiClient.invokeAPI(path, "DELETE", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
