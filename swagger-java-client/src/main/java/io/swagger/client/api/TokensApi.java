package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.Token;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-12T16:39:54.318-05:00")
public class TokensApi {
  private ApiClient apiClient;

  public TokensApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TokensApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * List all known tokens
   * List all tokens. Admin Only.
   * @return List<Token>
   */
  public List<Token> listTokens () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/auth/tokens".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Token>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Add a new bitbucket.org token, used by quay.io redirect
   * This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here
   * @param code 
   * @return Token
   */
  public Token addBitbucketToken (String code) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/auth/tokens/bitbucket.org".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "code", code));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh Bitbucket token
   * The Bitbucket token expire in one hour. When this happens you&#39;ll get 401 responses
   * @return Token
   */
  public Token refreshBitbucketToken () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/auth/tokens/bitbucket.org/refresh".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Add a new github.com token, used by quay.io redirect
   * This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here
   * @param code 
   * @return Token
   */
  public Token addGithubToken (String code) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/auth/tokens/github.com".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "code", code));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Add a new quay IO token
   * This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here
   * @param accessToken 
   * @return Token
   */
  public Token addQuayToken (String accessToken) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/auth/tokens/quay.io".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "access_token", accessToken));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a specific token by id
   * Get a specific token by id
   * @param tokenId ID of token to return
   * @return Token
   */
  public Token listToken (Long tokenId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'tokenId' is set
    if (tokenId == null) {
      throw new ApiException(400, "Missing the required parameter 'tokenId' when calling listToken");
    }
    
    // create path and map variables
    String path = "/auth/tokens/{tokenId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tokenId" + "\\}", apiClient.escapeString(tokenId.toString()));

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

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Deletes a token
   * 
   * @param tokenId Token id to delete
   * @return void
   */
  public void deleteToken (Long tokenId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'tokenId' is set
    if (tokenId == null) {
      throw new ApiException(400, "Missing the required parameter 'tokenId' when calling deleteToken");
    }
    
    // create path and map variables
    String path = "/auth/tokens/{tokenId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tokenId" + "\\}", apiClient.escapeString(tokenId.toString()));

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
