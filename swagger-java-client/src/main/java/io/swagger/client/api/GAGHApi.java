package io.swagger.client.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;
import io.swagger.client.model.Metadata;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolVersion;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-01T15:18:10.919-05:00")
public class GAGHApi {
  private ApiClient apiClient;

  public GAGHApi() {
    this(Configuration.getDefaultApiClient());
  }

  public GAGHApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * List all tools
   * This endpoint returns all tools available or a filtered subset using metadata query parameters.
   * @param registryId A unique identifier of the tool for this particular tool registry, for example `123456`
   * @param registry The image registry that contains the image.
   * @param organization The organization in the registry that published the image.
   * @param name The name of the image.
   * @param toolname The name of the tool.
   * @param description The description of the tool.
   * @param author The author of the tool (TODO a thought occurs, are we assuming that the author of the CWL and the image are the same?).
   * @return List<Tool>
   */
  public List<Tool> toolsGet (String registryId, String registry, String organization, String name, String toolname, String description, String author) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/api/v1/tools".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "registry-id", registryId));
    
    queryParams.addAll(apiClient.parameterToPairs("", "registry", registry));
    
    queryParams.addAll(apiClient.parameterToPairs("", "organization", organization));
    
    queryParams.addAll(apiClient.parameterToPairs("", "name", name));
    
    queryParams.addAll(apiClient.parameterToPairs("", "toolname", toolname));
    
    queryParams.addAll(apiClient.parameterToPairs("", "description", description));
    
    queryParams.addAll(apiClient.parameterToPairs("", "author", author));
    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Tool>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Return some metadata that is useful for describing this registry
   * Return some metadata that is useful for describing this registry
   * @return Metadata
   */
  public Metadata toolsMetadataGet () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/api/v1/tools/metadata".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Metadata>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List one specific tool, acts as an anchor for self references
   * This endpoint returns one specific tool (which has ToolVersions nested inside it)
   * @param registryId A unique identifier of the tool for this particular tool registry, for example `123456`
   * @return void
   */
  public void toolsRegistryIdGet (String registryId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'registryId' is set
    if (registryId == null) {
      throw new ApiException(400, "Missing the required parameter 'registryId' when calling toolsRegistryIdGet");
    }
    
    // create path and map variables
    String path = "/api/v1/tools/{registry-id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "registry-id" + "\\}", apiClient.escapeString(registryId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * List one specific tool version, acts as an anchor for self references
   * This endpoint returns one specific tool version
   * @param registryId A unique identifier of the tool for this particular tool registry, for example `123456`
   * @param versionId An identifier of the tool version for this particular tool registry, for example `v1`
   * @return ToolVersion
   */
  public ToolVersion toolsRegistryIdVersionVersionIdGet (String registryId, String versionId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'registryId' is set
    if (registryId == null) {
      throw new ApiException(400, "Missing the required parameter 'registryId' when calling toolsRegistryIdVersionVersionIdGet");
    }
    
    // verify the required parameter 'versionId' is set
    if (versionId == null) {
      throw new ApiException(400, "Missing the required parameter 'versionId' when calling toolsRegistryIdVersionVersionIdGet");
    }
    
    // create path and map variables
    String path = "/api/v1/tools/{registry-id}/version/{version-id}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "registry-id" + "\\}", apiClient.escapeString(registryId.toString()))
      .replaceAll("\\{" + "version-id" + "\\}", apiClient.escapeString(versionId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ToolVersion>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the tool descriptor (CWL/WDL) for the specified tool.
   * Returns the CWL or WDL descriptor for the specified tool.
   * @param registryId A unique identifier of the tool for this particular tool registry, for example `123456`
   * @param versionId An identifier of the tool version for this particular tool registry, for example `v1`
   * @param format The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output format to return.
   * @return ToolDescriptor
   */
  public ToolDescriptor toolsRegistryIdVersionVersionIdDescriptorGet (String registryId, String versionId, String format) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'registryId' is set
    if (registryId == null) {
      throw new ApiException(400, "Missing the required parameter 'registryId' when calling toolsRegistryIdVersionVersionIdDescriptorGet");
    }
    
    // verify the required parameter 'versionId' is set
    if (versionId == null) {
      throw new ApiException(400, "Missing the required parameter 'versionId' when calling toolsRegistryIdVersionVersionIdDescriptorGet");
    }
    
    // create path and map variables
    String path = "/api/v1/tools/{registry-id}/version/{version-id}/descriptor".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "registry-id" + "\\}", apiClient.escapeString(registryId.toString()))
      .replaceAll("\\{" + "version-id" + "\\}", apiClient.escapeString(versionId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "format", format));
    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ToolDescriptor>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the dockerfile for the specified image.
   * Returns the dockerfile for the specified image.
   * @param registryId A unique identifier of the tool for this particular tool registry, for example `123456`
   * @param versionId An identifier of the tool version for this particular tool registry, for example `v1`
   * @return ToolDockerfile
   */
  public ToolDockerfile toolsRegistryIdVersionVersionIdDockerfileGet (String registryId, String versionId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'registryId' is set
    if (registryId == null) {
      throw new ApiException(400, "Missing the required parameter 'registryId' when calling toolsRegistryIdVersionVersionIdDockerfileGet");
    }
    
    // verify the required parameter 'versionId' is set
    if (versionId == null) {
      throw new ApiException(400, "Missing the required parameter 'versionId' when calling toolsRegistryIdVersionVersionIdDockerfileGet");
    }
    
    // create path and map variables
    String path = "/api/v1/tools/{registry-id}/version/{version-id}/dockerfile".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "registry-id" + "\\}", apiClient.escapeString(registryId.toString()))
      .replaceAll("\\{" + "version-id" + "\\}", apiClient.escapeString(versionId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json", "text/plain"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<ToolDockerfile>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
