package io.dockstore.webservice.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.OrganizationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrganizationResourceTest {

    private User normalUser;
    private User adminUser;
    private User curatorUser;
    private User memberUser;

    private Organization approvedOrganization;
    private Organization pendingOrganization;
    private Organization rejectedOrganization;

    private Map<Long, User> idToUser;
    private Map<Long, Organization> idToOrganization;
    
    private UserDAO userDAO;
    private OrganizationDAO organizationDAO;

    private User mockUser(Long id, boolean isAdmin, boolean isCurator) {
        User user = Mockito.mock(User.class);

        when(user.getId()).thenReturn(id);
        when(user.getIsAdmin()).thenReturn(isAdmin);
        when(user.isCurator()).thenReturn(isCurator);

        idToUser.put(user.getId(), user);

        return (user);
    }

    private Organization mockOrganization(Long id, Organization.ApplicationState state, User member) {
        Organization organization = Mockito.mock(Organization.class);

        when(organization.getId()).thenReturn(id);
        when(organization.getStatus()).thenReturn(state);

        Set<OrganizationUser> organizationUsers = new HashSet<>();
        organizationUsers.add(mockOrganizationUser(organization, member));
        when(organization.getUsers()).thenReturn(organizationUsers);
            
        idToOrganization.put(organization.getId(), organization);

        return (organization);
    }

    private OrganizationUser mockOrganizationUser(Organization organization, User user) {
        OrganizationUser organizationUser = Mockito.mock(OrganizationUser.class);
        OrganizationUser.OrganizationUserId id = new OrganizationUser.OrganizationUserId(user.getId(), organization.getId());
        when(organizationUser.getId()).thenReturn(id);
        when(organizationUser.getUser()).thenReturn(user);
        when(organizationUser.getStatus()).thenReturn(OrganizationUser.InvitationStatus.ACCEPTED);
        when(organizationUser.getRole()).thenReturn(OrganizationUser.Role.MEMBER);

        return (organizationUser);
    }

    private UserDAO mockUserDAO() {
        UserDAO mockedUserDAO = Mockito.mock(UserDAO.class);
        when(mockedUserDAO.findById(anyLong())).thenAnswer(invocation -> {
            return (idToUser.get(invocation.getArgument(0)));
        });
        return (mockedUserDAO);
    }

    private OrganizationDAO mockOrganizationDAO() {
        OrganizationDAO mockedOrganizationDAO = Mockito.mock(OrganizationDAO.class);
        when(mockedOrganizationDAO.findById(anyLong())).thenAnswer(invocation -> {
            return (idToOrganization.get(invocation.getArgument(0)));
        });
        return (mockedOrganizationDAO);
    }

    @BeforeEach
    public void init() {
        idToUser = new HashMap<>();
        idToOrganization = new HashMap<>();

        normalUser = mockUser(1L, false, false);
        adminUser = mockUser(2L, true, false);
        curatorUser = mockUser(3L, false, true);
        memberUser = mockUser(4L, false, false);

        approvedOrganization = mockOrganization(10L,
            Organization.ApplicationState.APPROVED, memberUser);
        pendingOrganization = mockOrganization(11L,
            Organization.ApplicationState.PENDING, memberUser);
        rejectedOrganization = mockOrganization(12L,
            Organization.ApplicationState.REJECTED, memberUser);

        userDAO = mockUserDAO();
        organizationDAO = mockOrganizationDAO();
    }

    private void checkExists(Long organizationId, Long userId, boolean shouldExist) {
        boolean exists = OrganizationResource.doesOrganizationExistToUser(
            organizationId, userId, organizationDAO, userDAO);
        assertEquals(shouldExist, exists);
    }

    /**
     * Test that {@link OrganizationResource#doesOrganizationExistToUser} implements the correct organization visibility policy.
     */
    @Test
    void doesOrganizationExistToUserTest() {
        final Long bogusUserID = -1L;
        final Long bogusOrganizationID = -1L;

        // To avoid duplicating the logic we're testing, we explicitly
        // specify each of the test conditions and expected results below.

        checkExists(approvedOrganization.getId(), normalUser.getId(), true);
        checkExists(approvedOrganization.getId(), adminUser.getId(), true);
        checkExists(approvedOrganization.getId(), curatorUser.getId(), true);
        checkExists(approvedOrganization.getId(), memberUser.getId(), true);
        checkExists(approvedOrganization.getId(), bogusUserID, true);

        checkExists(pendingOrganization.getId(), normalUser.getId(), false);
        checkExists(pendingOrganization.getId(), adminUser.getId(), true);
        checkExists(pendingOrganization.getId(), curatorUser.getId(), true);
        checkExists(pendingOrganization.getId(), memberUser.getId(), true);
        checkExists(pendingOrganization.getId(), bogusUserID, false);

        checkExists(rejectedOrganization.getId(), normalUser.getId(), false);
        checkExists(rejectedOrganization.getId(), adminUser.getId(), true);
        checkExists(rejectedOrganization.getId(), curatorUser.getId(), true);
        checkExists(rejectedOrganization.getId(), memberUser.getId(), true);
        checkExists(rejectedOrganization.getId(), bogusUserID, false);

        checkExists(bogusOrganizationID, normalUser.getId(), false);
        checkExists(bogusOrganizationID, adminUser.getId(), false);
        checkExists(bogusOrganizationID, curatorUser.getId(), false);
        checkExists(bogusOrganizationID, memberUser.getId(), false);
        checkExists(bogusOrganizationID, bogusUserID, false);
    }
}
