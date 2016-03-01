package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.BitbucketOrgView;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-01T15:18:10.919-05:00")
public class IntegrationbitbucketorgApi {
  private ApiClient apiClient;

  public IntegrationbitbucketorgApi() {
    this(Configuration.getDefaultApiClient());
  }

  public IntegrationbitbucketorgApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * Display an authorization link for bitbucket.org
   * This is a stop-gap GUI for displaying a link that allows a user to start the OAuth 2 web flow
   * @return BitbucketOrgView
   */
  public BitbucketOrgView getView () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/integration.bitbucket.org".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "text/html"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<BitbucketOrgView>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
