package io.dockstore.webservice.helpers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

import io.dockstore.webservice.core.DeletedUsername;
import io.dockstore.webservice.jdbi.DeletedUsernameDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeletedUserHelper {
    // NIH suggests a 3 year limit before allowing username reuse.
    private static final Duration NO_REUSE_TIME_LIMIT = Duration.ofDays(355 * 3);
    private static final Logger LOG = LoggerFactory.getLogger(DeletedUserHelper.class);

    private DeletedUserHelper() {

    }

    public static boolean deletedUserFound(String username, DeletedUsernameDAO deletedUsernameDAO) {
        LocalDateTime threeYearsAgoDateTime = LocalDateTime.now().minusSeconds(NO_REUSE_TIME_LIMIT.getSeconds());
        Timestamp threeYearsAgoTimestamp = Timestamp.valueOf(threeYearsAgoDateTime);
        DeletedUsername deletedUsername = deletedUsernameDAO.findNonReusableUsername(username, threeYearsAgoTimestamp);
        if (deletedUsername != null) {
            return true;
        }
        return false;
    }
}
