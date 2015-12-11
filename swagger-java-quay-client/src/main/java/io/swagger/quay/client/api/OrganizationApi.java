package io.swagger.quay.client.api;

import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.Pair;
import io.swagger.quay.client.TypeRef;

import io.swagger.quay.client.model.UpdateOrg;
import io.swagger.quay.client.model.NewApp;
import io.swagger.quay.client.model.UpdateApp;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class OrganizationApi {
  private ApiClient apiClient;

  public OrganizationApi() {
    this(Configuration.getDefaultApiClient());
  }

  public OrganizationApi(ApiClient apiClient) {
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
   * Get information on the specified application.
   * @param clientId The OAuth client ID
   * @return void
   */
  public void getApplicationInformation (String clientId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'clientId' is set
    if (clientId == null) {
      throw new ApiException(400, "Missing the required parameter 'clientId' when calling getApplicationInformation");
    }
    
    // create path and map variables
    String path = "/api/v1/app/{client_id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "client_id" + "\\}", apiClient.escapeString(clientId.toString()));

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

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Get the details for the specified organization
   * @param orgname The name of the organization
   * @return void
   */
  public void getOrganization (String orgname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganization");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}".replaceAll("\\{format\\}","json")
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
   * Change the details for the specified organization.
   * @param orgname The name of the organization
   * @param body Request body contents.
   * @return void
   */
  public void changeOrganizationDetails (String orgname, UpdateOrg body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling changeOrganizationDetails");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling changeOrganizationDetails");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}".replaceAll("\\{format\\}","json")
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

    
    apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * List the applications for the specified organization
   * @param orgname The name of the organization
   * @return void
   */
  public void getOrganizationApplications (String orgname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationApplications");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/applications".replaceAll("\\{format\\}","json")
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
   * Creates a new application under this organization.
   * @param orgname The name of the organization
   * @param body Request body contents.
   * @return void
   */
  public void createOrganizationApplication (String orgname, NewApp body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling createOrganizationApplication");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling createOrganizationApplication");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/applications".replaceAll("\\{format\\}","json")
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
   * Retrieves the application with the specified client_id under the specified organization
   * @param orgname The name of the organization
   * @param clientId The OAuth client ID
   * @return void
   */
  public void getOrganizationApplication (String orgname, String clientId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationApplication");
    }
    
    // verify the required parameter 'clientId' is set
    if (clientId == null) {
      throw new ApiException(400, "Missing the required parameter 'clientId' when calling getOrganizationApplication");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/applications/{client_id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "client_id" + "\\}", apiClient.escapeString(clientId.toString()));

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
   * Updates an application under this organization.
   * @param orgname The name of the organization
   * @param clientId The OAuth client ID
   * @param body Request body contents.
   * @return void
   */
  public void updateOrganizationApplication (String orgname, String clientId, UpdateApp body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling updateOrganizationApplication");
    }
    
    // verify the required parameter 'clientId' is set
    if (clientId == null) {
      throw new ApiException(400, "Missing the required parameter 'clientId' when calling updateOrganizationApplication");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateOrganizationApplication");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/applications/{client_id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "client_id" + "\\}", apiClient.escapeString(clientId.toString()));

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
   * Deletes the application under this organization.
   * @param orgname The name of the organization
   * @param clientId The OAuth client ID
   * @return void
   */
  public void deleteOrganizationApplication (String orgname, String clientId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteOrganizationApplication");
    }
    
    // verify the required parameter 'clientId' is set
    if (clientId == null) {
      throw new ApiException(400, "Missing the required parameter 'clientId' when calling deleteOrganizationApplication");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/applications/{client_id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "client_id" + "\\}", apiClient.escapeString(clientId.toString()));

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
   * List the human members of the specified organization.
   * @param orgname The name of the organization
   * @return void
   */
  public void getOrganizationMembers (String orgname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationMembers");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/members".replaceAll("\\{format\\}","json")
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
   * Retrieves the details of a member of the organization.
   * @param orgname The name of the organization
   * @param membername The username of the organization member
   * @return void
   */
  public void getOrganizationMember (String orgname, String membername) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationMember");
    }
    
    // verify the required parameter 'membername' is set
    if (membername == null) {
      throw new ApiException(400, "Missing the required parameter 'membername' when calling getOrganizationMember");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/members/{membername}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "membername" + "\\}", apiClient.escapeString(membername.toString()));

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
   * Removes a member from an organization, revoking all its repository\n        priviledges and removing it from all teams in the organization.
   * @param orgname The name of the organization
   * @param membername The username of the organization member
   * @return void
   */
  public void removeOrganizationMember (String orgname, String membername) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling removeOrganizationMember");
    }
    
    // verify the required parameter 'membername' is set
    if (membername == null) {
      throw new ApiException(400, "Missing the required parameter 'membername' when calling removeOrganizationMember");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/members/{membername}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "membername" + "\\}", apiClient.escapeString(membername.toString()));

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
