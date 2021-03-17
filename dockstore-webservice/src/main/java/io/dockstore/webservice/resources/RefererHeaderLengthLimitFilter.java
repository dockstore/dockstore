package io.dockstore.webservice.resources;

import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import io.dockstore.webservice.CustomWebApplicationException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every request will be filtered through here to determine if the length of the referer header does exceed our limit.
 * This is done for two reasons:
 * 1. Prevent very long query parameters. For Dockstore, these should be relatively short for the most part.
 * 2. The search page/elastic search will begin fail if the search query becomes too long. Although these appear to be query parameters, the UI
 * doesn't send them in the request URL. The only way I could see the full url is to look at the referer header.
 */
public class RefererHeaderLengthLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPrivilegesFilter.class);
    private static final int LENGTH_LIMIT = 500;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext != null) {
            List<String> refererHeader = requestContext.getHeaders().get("Referer");
            if (refererHeader != null && !refererHeader.isEmpty()) {
                String referer = refererHeader.get(0);
                if (referer.length() > LENGTH_LIMIT) {
                    String msg = "Request exceeds url length limit.";
                    LOG.info(msg);
                    throw new CustomWebApplicationException(msg, HttpStatus.SC_REQUEST_TOO_LONG);
                }
            }
        }
    }
}
