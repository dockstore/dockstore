package io.swagger.quay.client.api;

import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.Pair;
import io.swagger.quay.client.TypeRef;


import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class SecscanApi {
  private ApiClient apiClient;

  public SecscanApi() {
    this(Configuration.getDefaultApiClient());
  }

  public SecscanApi(ApiClient apiClient) {
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
   * Fetches the packages added/removed in the given repo image.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param imageid The image ID
   * @return void
   */
  public void getRepoImagePackages (String repository, String imageid) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoImagePackages");
    }
    
    // verify the required parameter 'imageid' is set
    if (imageid == null) {
      throw new ApiException(400, "Missing the required parameter 'imageid' when calling getRepoImagePackages");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/image/{imageid}/packages".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()))
      .replaceAll("\\{" + "imageid" + "\\}", apiClient.escapeString(imageid.toString()));

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
   * Fetches the vulnerabilities (if any) for a repository tag.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param imageid The image ID
   * @param minimumPriority Minimum vulnerability priority
   * @return void
   */
  public void getRepoImageVulnerabilities (String repository, String imageid, String minimumPriority) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepoImageVulnerabilities");
    }
    
    // verify the required parameter 'imageid' is set
    if (imageid == null) {
      throw new ApiException(400, "Missing the required parameter 'imageid' when calling getRepoImageVulnerabilities");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/image/{imageid}/vulnerabilities".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()))
      .replaceAll("\\{" + "imageid" + "\\}", apiClient.escapeString(imageid.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "minimumPriority", minimumPriority));
    

    

    

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
