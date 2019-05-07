package io.dockstore.provision;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ro.fortsoft.pf4j.ExtensionPoint;

/**
 * Interface for preprovisioning. This is intended for schemes like the
 * Data Object Service (https://github.com/ga4gh/data-object-service-schemas),
 * where resolving a DOS URI returns a data object that has a list of urls, and
 * one of those urls is what needs to be provisioned.
 *
 * <p>
 *     Implementations of this interface will get called before implementations
 *     of the {@link ProvisionInterface}, with values being returned by this
 *     interface being passed on to <code>ProvisionInteface.downloadFrom</code>.
 * </p>
 */
public interface PreProvisionInterface extends ExtensionPoint {

    /**
     * <p>
     * Given a target path, return a list of target paths. For example, if
     * the target path is <code>dos://GUID</code>, this might return
     * <code>["s3://s3-url", "gs://gs-url", "http://http-url"]</code>.
     * </p>
     *
     * <p>
     * The order of the list is significant in that Dockstore will provision the first target path
     * that a file provisioning plugin supports. In the example above, the s3 url would be provisioned
     * even though Dockstore supports http urls, because the s3 url was first.
     * </p>
     *
     * <p>
     * The method should return an empty list if no transformation was done.
     * </p>
     *
     * @param targetPath
     * @return a list of targetPaths
     */
    List<String> prepareDownload(String targetPath);

    /**
     * Returns whether a particular file path should be handled by this plugin
     * @return return schemes that this preprovisioning interface handles (ex: dos)
     */
    Set<String> schemesHandled();

    /**
     * Optional method that can be overridden.
     *
     */
    default void setConfiguration(Map<String, String> config) {
        // No default implementation necessary
    }
}
