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

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class RobotApi {
  private ApiClient apiClient;

  public RobotApi() {
    this(Configuration.getDefaultApiClient());
  }

  public RobotApi(ApiClient apiClient) {
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
   * List the organization&#39;s robots.
   * @param orgname The name of the organization
   * @param permissions Whether to include repostories and teams in which the robots have permission.
   * @return void
   */
  public void getOrgRobots (String orgname, Boolean permissions) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrgRobots");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "permissions", permissions));
    

    

    

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
   * Returns the organization&#39;s robot with the specified name.
   * @param orgname The name of the organization
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void getOrgRobot (String orgname, String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrgRobot");
    }
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling getOrgRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Create a new robot in the organization.
   * @param orgname The name of the organization
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void createOrgRobot (String orgname, String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling createOrgRobot");
    }
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling createOrgRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Delete an existing organization robot.
   * @param orgname The name of the organization
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void deleteOrgRobot (String orgname, String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling deleteOrgRobot");
    }
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling deleteOrgRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Returns the list of repository permissions for the org&#39;s robot.
   * @param orgname The name of the organization
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void getOrgRobotPermissions (String orgname, String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling getOrgRobotPermissions");
    }
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling getOrgRobotPermissions");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots/{robot_shortname}/permissions".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Regenerates the token for an organization robot.
   * @param orgname The name of the organization
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void regenerateOrgRobotToken (String orgname, String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'orgname' is set
    if (orgname == null) {
      throw new ApiException(400, "Missing the required parameter 'orgname' when calling regenerateOrgRobotToken");
    }
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling regenerateOrgRobotToken");
    }
    
    // create path and map variables
    String path = "/api/v1/organization/{orgname}/robots/{robot_shortname}/regenerate".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "orgname" + "\\}", apiClient.escapeString(orgname.toString()))
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * List the available robots for the user.
   * @param permissions Whether to include repostories and teams in which the robots have permission.
   * @return void
   */
  public void getUserRobots (Boolean permissions) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/api/v1/user/robots".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "permissions", permissions));
    

    

    

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
   * Returns the user&#39;s robot with the specified name.
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void getUserRobot (String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling getUserRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/user/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Create a new user robot with the specified name.
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void createUserRobot (String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling createUserRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/user/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Delete an existing robot.
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void deleteUserRobot (String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling deleteUserRobot");
    }
    
    // create path and map variables
    String path = "/api/v1/user/robots/{robot_shortname}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Returns the list of repository permissions for the user&#39;s robot.
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void getUserRobotPermissions (String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling getUserRobotPermissions");
    }
    
    // create path and map variables
    String path = "/api/v1/user/robots/{robot_shortname}/permissions".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
   * Regenerates the token for a user&#39;s robot.
   * @param robotShortname The short name for the robot, without any user or organization prefix
   * @return void
   */
  public void regenerateUserRobotToken (String robotShortname) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'robotShortname' is set
    if (robotShortname == null) {
      throw new ApiException(400, "Missing the required parameter 'robotShortname' when calling regenerateUserRobotToken");
    }
    
    // create path and map variables
    String path = "/api/v1/user/robots/{robot_shortname}/regenerate".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "robot_shortname" + "\\}", apiClient.escapeString(robotShortname.toString()));

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
