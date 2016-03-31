package io.swagger.client.api;

import io.swagger.client.ApiException;
import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.Pair;
import io.swagger.client.TypeRef;

import io.swagger.client.model.Workflow;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Body1;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.User;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-31T13:11:43.123-04:00")
public class WorkflowsApi {
  private ApiClient apiClient;

  public WorkflowsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public WorkflowsApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * List all workflows cached in database
   * List workflows currently known. Admin Only
   * @return List<Workflow>
   */
  public List<Workflow> allWorkflows () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/workflows".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Workflow>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Manually register a workflow
   * Manually register workflow (public or private).
   * @param workflowRegistry Workflow registry
   * @param workflowPath Workflow repository
   * @param defaultWorkflowPath Workflow container new descriptor path (CWL or WDL) and/or name
   * @param workflowName Workflow name
   * @return Workflow
   */
  public Workflow manualRegister (String workflowRegistry, String workflowPath, String defaultWorkflowPath, String workflowName) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowRegistry' is set
    if (workflowRegistry == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowRegistry' when calling manualRegister");
    }
    
    // verify the required parameter 'workflowPath' is set
    if (workflowPath == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowPath' when calling manualRegister");
    }
    
    // verify the required parameter 'defaultWorkflowPath' is set
    if (defaultWorkflowPath == null) {
      throw new ApiException(400, "Missing the required parameter 'defaultWorkflowPath' when calling manualRegister");
    }
    
    // verify the required parameter 'workflowName' is set
    if (workflowName == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowName' when calling manualRegister");
    }
    
    // create path and map variables
    String path = "/workflows/manualRegister".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "workflowRegistry", workflowRegistry));
    
    queryParams.addAll(apiClient.parameterToPairs("", "workflowPath", workflowPath));
    
    queryParams.addAll(apiClient.parameterToPairs("", "defaultWorkflowPath", defaultWorkflowPath));
    
    queryParams.addAll(apiClient.parameterToPairs("", "workflowName", workflowName));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a workflow by path
   * Lists info of workflow. Enter full path.
   * @param repository repository path
   * @return Workflow
   */
  public Workflow getWorkflowByPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getWorkflowByPath");
    }
    
    // create path and map variables
    String path = "/workflows/path/workflow/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a workflow by path
   * Lists info of workflow. Enter full path.
   * @param repository repository path
   * @return Workflow
   */
  public Workflow getPublishedWorkflowByPath (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getPublishedWorkflowByPath");
    }
    
    // create path and map variables
    String path = "/workflows/path/workflow/{repository}/published".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * List all published workflows.
   * NO authentication
   * @return List<Workflow>
   */
  public List<Workflow> allPublishedWorkflows () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/workflows/published".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Workflow>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a published workflow
   * NO authentication
   * @param workflowId Workflow ID
   * @return Workflow
   */
  public Workflow getPublishedWorkflow (Long workflowId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling getPublishedWorkflow");
    }
    
    // create path and map variables
    String path = "/workflows/published/{workflowId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh all workflows
   * Updates some metadata. ADMIN ONLY
   * @return List<Workflow>
   */
  public List<Workflow> refreshAll () throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/workflows/refresh".replaceAll("\\{format\\}","json");

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

    
    TypeRef returnType = new TypeRef<List<Workflow>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Search for matching published workflows.
   * Search on the name (full path name) and description. NO authentication
   * @param pattern 
   * @return List<Workflow>
   */
  public List<Workflow> search (String pattern) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/workflows/search".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "pattern", pattern));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<List<Workflow>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get a registered workflow
   * 
   * @param workflowId workflow ID
   * @return Workflow
   */
  public Workflow getWorkflow (Long workflowId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling getWorkflow");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Update the tool with the given workflow.
   * 
   * @param workflowId Workflow to modify.
   * @param body Workflow with updated information
   * @return Workflow
   */
  public Workflow updateWorkflow (Long workflowId, Workflow body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling updateWorkflow");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateWorkflow");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the corresponding Dockstore.cwl file on Github.
   * Does not need authentication
   * @param workflowId Tool id
   * @param tag 
   * @return SourceFile
   */
  public SourceFile cwl (Long workflowId, String tag) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling cwl");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/cwl".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "tag", tag));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<SourceFile>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Update the labels linked to a workflow.
   * Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.
   * @param workflowId Tool to modify.
   * @param labels Comma-delimited list of labels.
   * @param body This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.
   * @return Workflow
   */
  public Workflow updateLabels (Long workflowId, String labels, Body1 body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling updateLabels");
    }
    
    // verify the required parameter 'labels' is set
    if (labels == null) {
      throw new ApiException(400, "Missing the required parameter 'labels' when calling updateLabels");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/labels".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "labels", labels));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Publish or unpublish a workflow
   * Publish/publish a workflow (public or private).
   * @param workflowId Tool id to publish/unpublish
   * @param body PublishRequest to refresh the list of repos for a user
   * @return Workflow
   */
  public Workflow publish (Long workflowId, PublishRequest body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling publish");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling publish");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/publish".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Refresh one particular workflow. Always do a full refresh when targetted
   * 
   * @param workflowId workflow ID
   * @return Workflow
   */
  public Workflow refresh (Long workflowId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling refresh");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/refresh".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<Workflow>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get users of a workflow
   * 
   * @param workflowId workflow ID
   * @return List<User>
   */
  public List<User> getUsers (Long workflowId) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling getUsers");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/users".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

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

    
    TypeRef returnType = new TypeRef<List<User>>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
  /**
   * Get the corresponding Dockstore.wdl file on Github.
   * Does not need authentication
   * @param workflowId Tool id
   * @param tag 
   * @return SourceFile
   */
  public SourceFile wdl (Long workflowId, String tag) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'workflowId' is set
    if (workflowId == null) {
      throw new ApiException(400, "Missing the required parameter 'workflowId' when calling wdl");
    }
    
    // create path and map variables
    String path = "/workflows/{workflowId}/wdl".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "workflowId" + "\\}", apiClient.escapeString(workflowId.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "tag", tag));
    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      "application/json"
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    TypeRef returnType = new TypeRef<SourceFile>() {};
    return apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    
  }
  
}
