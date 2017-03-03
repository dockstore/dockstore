package io.dockstore.webservice.resources.proposedGA4GH;

/**
 * Created by kcao on 01/03/17.
 */
public class ToolsApiExtendedServiceFactory {
    private final static ToolsExtendedApiService service = new ToolsApiExtendedServiceImpl();

    public static ToolsExtendedApiService getToolsExtendedApi() {
        return service;
    }
}
