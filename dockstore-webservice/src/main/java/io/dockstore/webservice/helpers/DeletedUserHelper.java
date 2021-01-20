package io.dockstore.webservice.helpers;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import io.dockstore.webservice.core.DeletedUsername;
import io.dockstore.webservice.jdbi.DeletedUsernameDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeletedUserHelper {
    // NIH suggests a 3 year limit before allowing username reuse.
    private static final int NO_REUSE_TIME_LIMIT = 365 * 3;
    private static final Logger LOG = LoggerFactory.getLogger(DeletedUserHelper.class);

    private DeletedUserHelper() {

    }

    public static boolean deletedUserFound(String username, DeletedUsernameDAO deletedUsernameDAO) {
        DeletedUsername deletedUsername = deletedUsernameDAO.findByUsername(username);
        if (deletedUsername != null) {
            LocalDateTime threeYearsAgoDateTime = LocalDateTime.now().minusDays(NO_REUSE_TIME_LIMIT);
            Timestamp threeYearsAgoTimestamp = Timestamp.valueOf(threeYearsAgoDateTime);
            if (deletedUsername.getDbCreateDate().before(threeYearsAgoTimestamp)) {
                LOG.info("Username of deleted user is old enough to be used again. Deleting record of deleted user.");
                deletedUsernameDAO.delete(deletedUsername);
                return false;
            }
            return true;
        }
        return false;
    }
}
