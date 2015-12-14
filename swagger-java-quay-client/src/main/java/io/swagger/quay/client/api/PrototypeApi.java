package io.swagger.quay.client.api;

import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.Pair;
import io.swagger.quay.client.TypeRef;

import io.swagger.quay.client.model.NewPrototype;
import io.swagger.quay.client.model.PrototypeUpdate;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class PrototypeApi {
  private ApiClient apiClient;

  public PrototypeApi() {
    this(Configuration.getDefaultApiClient());
  }

  public PrototypeApi(ApiClient apiClient) {
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
   * List the existing prototypes for this organization.
   * @param orgname The name of the organization
   * @return void
   */
  public void getOrganizationPrototypePermissions (String orgname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationPrototypePermissions");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/prototypes".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()));

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
   * Create a new permission prototype.
   * @param orgname The name of the organization
   * @param body Request body contents.
   * @return void
   */
  public void createOrganizationPrototypePermission (String orgname, NewPrototype body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling createOrganizationPrototypePermission");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling createOrganizationPrototypePermission");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/prototypes".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()));

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
   * Update the role of an existing permission prototype.
   * @param orgname The name of the organization
   * @param prototypeid The ID of the prototype
   * @param body Request body contents.
   * @return void
   */
  public void updateOrganizationPrototypePermission (String orgname, String prototypeid, PrototypeUpdate body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling updateOrganizationPrototypePermission");
    }
    
    // verify the required parameter 'prototypeid' is set
    if (prototypeid == null) {
      throw new ApiException(400, "Missing the required parameter 'prototypeid' when calling updateOrganizationPrototypePermission");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateOrganizationPrototypePermission");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/prototypes/{prototypeid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "prototypeid" + "\\}", apiClient.escapeString(prototypeid.toString()));

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
   * Delete an existing permission prototype.
   * @param orgname The name of the organization
   * @param prototypeid The ID of the prototype
   * @return void
   */
  public void deleteOrganizationPrototypePermission (String orgname, String prototypeid) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteOrganizationPrototypePermission");
    }
    
    // verify the required parameter 'prototypeid' is set
    if (prototypeid == null) {
      throw new ApiException(400, "Missing the required parameter 'prototypeid' when calling deleteOrganizationPrototypePermission");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/prototypes/{prototypeid}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "prototypeid" + "\\}", apiClient.escapeString(prototypeid.toString()));

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
  
}
