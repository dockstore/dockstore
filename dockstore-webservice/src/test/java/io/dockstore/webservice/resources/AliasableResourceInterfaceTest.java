package io.dockstore.webservice.resources;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import org.junit.Test;

import static org.junit.Assert.fail;

public class AliasableResourceInterfaceTest {

    @Test
    public void testAddAliasWithBadPrefix() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES)
                .forEach(invalidPrefix -> {
                    String invalidAlias = invalidPrefix + "_some_random_alias_suffix";
                    // Make sure the alias is valid
                    // If it is not acceptable then an exception is generated
                    Set<String> aliasSet = new HashSet<>();
                    aliasSet.add(invalidAlias);

                    boolean throwsError = false;
                    try {
                        AliasableResourceInterface.checkAliases(aliasSet, user);
                    } catch (CustomWebApplicationException ex) {
                        throwsError = true;
                    }

                    if (!throwsError) {
                        fail("An alias with an invalid prefix " + invalidPrefix + " was reported to be OK.");
                    }
                });
    }

    @Test
    public void testAdminCuratorAddAliasWithBadPrefix() {
        testAdminCuratorAddAliasWithBadPrefix(true, false);
        testAdminCuratorAddAliasWithBadPrefix(false, true);
    }

    public void testAdminCuratorAddAliasWithBadPrefix(boolean isAdmin, boolean isCurator) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        user.setCurator(isCurator);

        Set<String> alisesWithInvalidPrefixes = Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES)
                .map(invalidPrefix -> invalidPrefix + "_some_random_alias_suffix")
                .collect(Collectors.toSet());

        boolean throwsError = false;
        try {
            AliasableResourceInterface.checkAliases(alisesWithInvalidPrefixes, user);
        } catch (CustomWebApplicationException ex) {
            throwsError = true;
        }

        if (throwsError) {
            fail("An alias with an invalid prefix was reported unacceptable for an admin or curator. "
                    + "Invalid prefixes are acceptable for admins or curators");
        }
    }
}
