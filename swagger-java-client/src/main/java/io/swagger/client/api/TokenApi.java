package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.*;

import java.util.*;

import io.swagger.client.model.Token;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-10-13T13:30:22.221-04:00")
public class TokenApi {
  private ApiClient apiClient;

  public TokenApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TokenApi(ApiClient apiClient) {
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
   * List all tokens
   * @return List<Token>
   */
  public List<Token> listTokens () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/token".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Token>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Assign the token to a enduser
   * Temporary way to assign tokens to the endusers
   * @param tokenId 
   * @param userId 
   * @return Token
   */
  public Token assignUser (Long tokenId, Long userId) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/token/assignEnduser".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "tokenId", tokenId));
    
    queryParams.addAll(apiClient.parameterToPairs("", "user_id", userId));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all known tokens by source
   * List all tokens from a particular source
   * @param source source of tokens to return
   * @return List<Token>
   */
  public List<Token> listTokensBySource (String source) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'source' is set
    if (source == null) {
      throw new ApiException(400, "Missing the required parameter 'source' when calling listTokensBySource");
    }
    
    // create path and map variables
    String path = "/token/findBySource/{source}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "source" + "\\}", apiClient.escapeString(source.toString()));

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

    
    TypeRef returnType = new TypeRef<List<Token>>() {};
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
    String path = "/token/github.com".replaceAll("\\{format\\}","json");

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
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all tokens owned by the logged-in user
   * List the tokens owned by the logged in user
   * @return List<Token>
   */
  public List<Token> listOwnedTokens () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/token/listOwned".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Token>>() {};
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
    String path = "/token/quay.io".replaceAll("\\{format\\}","json");

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
    String path = "/token/{tokenId}".replaceAll("\\{format\\}","json")
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
   * @param apiKey 
   * @return Token
   */
  public Token deleteToken (Long tokenId, String apiKey) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'tokenId' is set
    if (tokenId == null) {
      throw new ApiException(400, "Missing the required parameter 'tokenId' when calling deleteToken");
    }
    
    // create path and map variables
    String path = "/token/{tokenId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "tokenId" + "\\}", apiClient.escapeString(tokenId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    if (apiKey != null)
    headerParams.put("api_key", apiClient.parameterToString(apiKey));
    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Token>() {};
    return apiClient.invokeAPI(path, "DELETE", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
