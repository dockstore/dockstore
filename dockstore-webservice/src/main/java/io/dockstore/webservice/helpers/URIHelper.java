package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class URIHelper {

    private static final Logger LOG = LoggerFactory.getLogger(URIHelper.class);

    private URIHelper() {
    }

    /**
     * Construct a base URL
     * @param aScheme scheme, e.g. http or https
     * @param aHostName host name, e.g. dockstore.org
     * @param aPort port, e.g. 4200
     */
    public static String createBaseUrl(String aScheme, String aHostName, String aPort) {
        URL url;
        try {
            int iport = StringUtils.isEmpty(aPort) ? -1 : Integer.parseInt(aPort);
            url = new URL(aScheme, aHostName, iport, "");
        } catch (MalformedURLException e) {
            LOG.error("Could not create base URL. Error is " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not create base URL."
                    + " Error is " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return url.toString();
    }
}
