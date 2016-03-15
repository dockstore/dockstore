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

import io.swagger.quay.client.model.TeamDescription;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class TeamApi {
  private ApiClient apiClient;

  public TeamApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TeamApi(ApiClient apiClient) {
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
   * Update the org-wide permission for the specified team.
   * @param orgname The name of the organization
   * @param teamname The name of the team
   * @param body Request body contents.
   * @return void
   */
  public void updateOrganizationTeam (String orgname, String teamname, TeamDescription body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling updateOrganizationTeam");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling updateOrganizationTeam");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateOrganizationTeam");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
   * Delete the specified team.
   * @param orgname The name of the organization
   * @param teamname The name of the team
   * @return void
   */
  public void deleteOrganizationTeam (String orgname, String teamname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteOrganizationTeam");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling deleteOrganizationTeam");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
   * Invites an email address to an existing team.
   * @param orgname 
   * @param email 
   * @param teamname 
   * @return void
   */
  public void inviteTeamMemberEmail (String orgname, String email, String teamname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling inviteTeamMemberEmail");
    }
    
    // verify the required parameter 'email' is set
    if (email == null) {
      throw new ApiException(400, "Missing the required parameter 'email' when calling inviteTeamMemberEmail");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling inviteTeamMemberEmail");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}/invite/{email}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "email" + "\\}", apiClient.escapeString(email.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
   * Delete an invite of an email address to join a team.
   * @param orgname 
   * @param email 
   * @param teamname 
   * @return void
   */
  public void deleteTeamMemberEmailInvite (String orgname, String email, String teamname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteTeamMemberEmailInvite");
    }
    
    // verify the required parameter 'email' is set
    if (email == null) {
      throw new ApiException(400, "Missing the required parameter 'email' when calling deleteTeamMemberEmailInvite");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling deleteTeamMemberEmailInvite");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}/invite/{email}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "email" + "\\}", apiClient.escapeString(email.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
   * Retrieve the list of members for the specified team.
   * @param orgname The name of the organization
   * @param teamname The name of the team
   * @param includePending Whether to include pending members
   * @return void
   */
  public void getOrganizationTeamMembers (String orgname, String teamname, Boolean includePending) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrganizationTeamMembers");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling getOrganizationTeamMembers");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}/members".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "includePending", includePending));
    

    

    

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
   * Adds or invites a member to an existing team.
   * @param orgname The name of the organization
   * @param membername The username of the team member
   * @param teamname The name of the team
   * @return void
   */
  public void updateOrganizationTeamMember (String orgname, String membername, String teamname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling updateOrganizationTeamMember");
    }
    
    // verify the required parameter 'membername' is set
    if (membername == null) {
      throw new ApiException(400, "Missing the required parameter 'membername' when calling updateOrganizationTeamMember");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling updateOrganizationTeamMember");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}/members/{membername}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "membername" + "\\}", apiClient.escapeString(membername.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
   * Delete a member of a team. If the user is merely invited to join\n        the team, then the invite is removed instead.
   * @param orgname The name of the organization
   * @param membername The username of the team member
   * @param teamname The name of the team
   * @return void
   */
  public void deleteOrganizationTeamMember (String orgname, String membername, String teamname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteOrganizationTeamMember");
    }
    
    // verify the required parameter 'membername' is set
    if (membername == null) {
      throw new ApiException(400, "Missing the required parameter 'membername' when calling deleteOrganizationTeamMember");
    }
    
    // verify the required parameter 'teamname' is set
    if (teamname == null) {
      throw new ApiException(400, "Missing the required parameter 'teamname' when calling deleteOrganizationTeamMember");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/team/{teamname}/members/{membername}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "membername" + "\\}", apiClient.escapeString(membername.toString()))
      .replaceAll("\\{" + "teamname" + "\\}", apiClient.escapeString(teamname.toString()));

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
