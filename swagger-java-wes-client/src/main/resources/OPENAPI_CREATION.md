##How WES OpenApi 3.0 yaml was created from Swagger 2.0 yaml

SwaggerHub was used to convert the WES 1.0.0 Swagger 2.0 yaml to OpenAPI 3.0 yaml

####Steps to convert:
1. Launch SwaggerHub https://app.swaggerhub.com/home
2. Create an account and login to SwaggerHub
3. Click on the 'plus' (**+**) icon on the left side of the page
3. Select 'Import and Document API'
4. In the Import API dialog paste the WES Swagger 2.0 URL: 
https://raw.githubusercontent.com/ga4gh/workflow-execution-service-schemas/1.0.0/openapi/workflow_execution_service.swagger.yaml into the
 'Path or URL' edit box
5. Click 'Import'
..You should now be able to see the The WES Swagger 2.0 yaml in the editor
6. Click on the green box with the down arrow at the top right of the editor
7. Select 'Convert to OpenAPI 3.0'
8. In the 'Convert to OpenAPI 3.0?' dialog click 'Convert & Update'
9. Click the 'Export^' button on the uppper right hand area of the page
10. Select '< Download API' and then 'YAML Unresolved' ..The yaml file will be downloaded to your local machine

**NOTE:**
In the OpenAPI 3.0 yaml POST 'runs' request the workflow_params type was changed from 'string' to binary and the 'format' was removed to 
enable the code generator to add the contents of the workflow params to a request body and not just the URI of the workflow_params. This 
seems to be what was intended from looking at the example curl commands for running workflows.  