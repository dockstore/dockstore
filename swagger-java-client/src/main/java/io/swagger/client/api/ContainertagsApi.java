package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.Tag;
import java.util.*;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-16T12:02:55.364-04:00")
public class ContainertagsApi {
  private ApiClient apiClient;

  public ContainertagsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ContainertagsApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * Get tags  for a container by id
   * Lists tags for a container. Enter full path (include quay.io in path).
   * @param containerId Tool to modify.
   * @return List<Tag>
   */
  public List<Tag> getTagsByPath (Long containerId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling getTagsByPath");
    }
    
    // create path and map variables
    String path = "/containers/path/{containerId}/tags".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<List<Tag>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Update the tags linked to a container
   * Tag correspond to each row of the versions table listing all information for a docker repo tag
   * @param containerId Tool to modify.
   * @param body List of modified tags
   * @return List<Tag>
   */
  public List<Tag> updateTags (Long containerId, List<Tag> body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling updateTags");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateTags");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/tags".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<List<Tag>>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Add new tags linked to a container
   * Tag correspond to each row of the versions table listing all information for a docker repo tag
   * @param containerId Tool to modify.
   * @param body List of new tags
   * @return List<Tag>
   */
  public List<Tag> addTags (Long containerId, List<Tag> body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling addTags");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling addTags");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/tags".replaceAll("\\{format\\}","json")
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

    
    TypeRef returnType = new TypeRef<List<Tag>>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Delete tag linked to a container
   * Tag correspond to each row of the versions table listing all information for a docker repo tag
   * @param containerId Tool to modify.
   * @param tagId Tag to delete
   * @return void
   */
  public void deleteTags (Long containerId, Long tagId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'containerId' is set
    if (containerId == null) {
      throw new ApiException(400, "Missing the required parameter 'containerId' when calling deleteTags");
    }
    
    // verify the required parameter 'tagId' is set
    if (tagId == null) {
      throw new ApiException(400, "Missing the required parameter 'tagId' when calling deleteTags");
    }
    
    // create path and map variables
    String path = "/containers/{containerId}/tags/{tagId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "containerId" + "\\}", apiClient.escapeString(containerId.toString()))
      .replaceAll("\\{" + "tagId" + "\\}", apiClient.escapeString(tagId.toString()));

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
  
}
