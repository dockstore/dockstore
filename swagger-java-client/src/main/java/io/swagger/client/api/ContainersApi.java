package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.*;

import java.util.*;

import io.swagger.client.model.Container;
import io.swagger.client.model.Collab;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-10-27T16:41:14.927-04:00")
public class ContainersApi {
  private ApiClient apiClient;

  public ContainersApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ContainersApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * List all docker containers cached in database
   * List docker container repos currently known. Admin Only
   * @return List<Container>
   */
  public List<Container> allContainers () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/containers".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a registered container by path
   * Lists info of container. Enter full path (include quay.io in path).
   * @param repository 
   * @return Container
   */
  public Container getRegisteredContainer (String repository) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/containers/path/{path}".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh all repos
   * Updates some metadata. ADMIN ONLY
   * @return List<Container>
   */
  public List<Container> refreshAll () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/containers/refresh".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all registered containers. This would be a minimal resource that would need to be implemented by a GA4GH reference server
   * NO authentication
   * @return List<Container>
   */
  public List<Container> allRegisteredContainers () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/containers/registered".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a registered container
   * NO authentication
   * @param containerId Container ID
   * @return Container
   */
  public Container getRegisteredContainer_1 (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling getRegisteredContainer_1");
    }
    
    // create path and map variables
    String path = "/containers/registered/{containerId}".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Search for matching registered containers. This would be a minimal resource that would need to be implemented by a GA4GH reference server
   * Search on the name (full path name) and description. NO authentication
   * @param pattern 
   * @return List<Container>
   */
  public List<Container> search (String pattern) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/containers/search".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a cached repo
   * 
   * @param containerId Container ID
   * @return Container
   */
  public Container getContainer (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling getContainer");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the corresponding collab.cwl file on Github
   * Enter full path of container (add quay.io if using quay.io)
   * @param containerId Container id to delete
   * @return Collab
   */
  public Collab collab (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling collab");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/collab".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<Collab>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Register a container
   * Register a container (public or private). Assumes that user is using quay.io and github. Include quay.io in path if using quay.io
   * @param containerId Container id to delete
   * @return Container
   */
  public Container register (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling register");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/register".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Unregister a container
   * 
   * @param containerId Container id to delete
   * @return Container
   */
  public Container unregister (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling unregister");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/unregister".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
