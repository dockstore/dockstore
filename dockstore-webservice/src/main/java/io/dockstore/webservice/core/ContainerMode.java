package io.dockstore.webservice.core;

/**
 * This enumerates the types of containers (really, images) that
 * we can add to the dockstore. This will become more prominent later if
 * we proceed with privacy and http link support.
 * @author dyuen
 */
public enum ContainerMode {
    /**
     * from quay.io automated builds or not, try to track back to source control regardless of
     * whether it is github or bitbucket and find Dockerfiles and dockstore.cwl 
     * if automated, track back to git identifier via quay.io API, find documents in default location specified by wizard
     * if not automated, cannot track back, skip until specified, find documents in default location specified by wizard 
     */
    AUTO_DETECT_QUAY_TAGS,
    /**
     * from quay.io or Docker Hub, the user simply enters an image path (ex: org/foobar or quay.io/org/foobar)
     * and then picks a source repo and then enters most remaining info (source tag, image tag, paths)
     */
    MANUAL_IMAGE_PATH
}
