package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.Container;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Body;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.User;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-02T16:12:35.652-05:00")
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a container by tool path
   * Lists info of container. Enter full path (include quay.io in path).
   * @param repository repository path
   * @return Container
   */
  public Container getContainerByToolPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getContainerByToolPath");
    }
    
    // create path and map variables
    String path = "/containers/path/tool/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a container by tool path
   * Lists info of container. Enter full path (include quay.io in path).
   * @param repository repository path
   * @return Container
   */
  public Container getRegisteredContainerByToolPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRegisteredContainerByToolPath");
    }
    
    // create path and map variables
    String path = "/containers/path/tool/{repository}/registered".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a list of containers by path
   * Lists info of container. Enter full path (include quay.io in path).
   * @param repository repository path
   * @return List<Container>
   */
  public List<Container> getContainerByPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getContainerByPath");
    }
    
    // create path and map variables
    String path = "/containers/path/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a registered container by path
   * NO authentication
   * @param repository repository path
   * @return List<Container>
   */
  public List<Container> getRegisteredContainerByPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRegisteredContainerByPath");
    }
    
    // create path and map variables
    String path = "/containers/path/{repository}/registered".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Container>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Register an image manually, along with tags
   * Register/publish an image manually.
   * @param body Container to be registered
   * @return Container
   */
  public Container registerManual (Container body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling registerManual");
    }
    
    // create path and map variables
    String path = "/containers/registerManual".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all registered containers.
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
      "application/json"
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
  public Container getRegisteredContainer (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling getRegisteredContainer");
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Search for matching registered containers.
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
      "application/json"
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Update the container with the given container.
   * 
   * @param containerId Container to modify.
   * @param body Container with updated information
   * @return Container
   */
  public Container updateContainer (Long containerId, Container body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling updateContainer");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateContainer");
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Delete manually registered image
   * 
   * @param containerId Container id to delete
   * @return void
   */
  public void deleteContainer (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling deleteContainer");
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "DELETE", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * Get the corresponding Dockstore.cwl file on Github.
   * Does not need authentication
   * @param containerId Container id
   * @param tag 
   * @return SourceFile
   */
  public SourceFile cwl (Long containerId, String tag) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling cwl");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/cwl".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "containerId" + "\\}", apiClient.escapeString(containerId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "tag", tag));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<SourceFile>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the corresponding Dockerfile on Github.
   * Does not need authentication
   * @param containerId Container id
   * @param tag 
   * @return SourceFile
   */
  public SourceFile dockerfile (Long containerId, String tag) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling dockerfile");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/dockerfile".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "containerId" + "\\}", apiClient.escapeString(containerId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "tag", tag));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<SourceFile>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Update the labels linked to a container.
   * Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.
   * @param containerId Container to modify.
   * @param labels Comma-delimited list of labels.
   * @param body This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.
   * @return Container
   */
  public Container updateLabels (Long containerId, String labels, Body body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling updateLabels");
    }
    
    // verify the required parameter 'labels' is set
    if (labels == null) {
      throw new ApiException(400, "Missing the required parameter 'labels' when calling updateLabels");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/labels".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "containerId" + "\\}", apiClient.escapeString(containerId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "labels", labels));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh one particular repo
   * 
   * @param containerId Container ID
   * @return Container
   */
  public Container refresh (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling refresh");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/refresh".replaceAll("\\{format\\}","json")
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Register or unregister a container
   * Register/publish a container (public or private). Assumes that user is using quay.io and github.
   * @param containerId Container id to register/publish
   * @param body RegisterRequest to refresh the list of repos for a user
   * @return Container
   */
  public Container register (Long containerId, RegisterRequest body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling register");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling register");
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Container>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get users of a container
   * 
   * @param containerId Container ID
   * @return List<User>
   */
  public List<User> getUsers (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling getUsers");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/users".replaceAll("\\{format\\}","json")
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
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<User>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
