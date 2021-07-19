package io.dockstore.webservice.resources;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class AliasableResourceInterfaceTest {
    private static final String[] ZENODO_DOI_ALIASES = {"10.5281/zenodo.6094", "10.5281/zenodo.60943", "10.5281/zenodo.6094355",
        "10.5281-zenodo.6094", "10.5281-zenodo.60943", "10.5281-zenodo.6094355"};


    @Test
    public void testCheckAliasWithBadPrefix() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES).forEach(invalidPrefix -> {
            String invalidAlias = invalidPrefix + "_some_random_alias_suffix";
            // Make sure the alias is valid
            // If it is not acceptable then an exception is generated
            try {
                AliasableResourceInterface.checkAliases(Collections.singleton(invalidPrefix + "_some_random_alias_suffix"),
                        user, true);
                fail("An alias with an invalid prefix " + invalidPrefix + " was reported to be OK.");
            } catch (CustomWebApplicationException ex) {
                assertTrue(ex.getErrorMessage().contains("Please create aliases without these prefixes"));
            }
        });
    }

    @Test
    public void testAdminCuratorCheckAliasWithBadPrefix() {
        // An alias with an invalid prefix can be created by an admin or curator.
        testAdminCuratorCheckAliasWithBadPrefix(true, false);
        testAdminCuratorCheckAliasWithBadPrefix(false, true);
    }

    private void testAdminCuratorCheckAliasWithBadPrefix(boolean isAdmin, boolean isCurator) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        user.setCurator(isCurator);

        Set<String> alisesWithInvalidPrefixes = Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES)
                .map(invalidPrefix -> invalidPrefix + "_some_random_alias_suffix").collect(Collectors.toSet());

        AliasableResourceInterface.checkAliases(alisesWithInvalidPrefixes, user, true);
    }

    @Test
    public void testCheckAliasWithForbiddenFormat() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Arrays.stream(ZENODO_DOI_ALIASES).forEach(zenodoDOI -> {
            // Make sure the alias is valid
            // If it is not acceptable then an exception is generated
            try {

                AliasableResourceInterface.checkAliases(Collections.singleton(zenodoDOI), user, true);
            } catch (CustomWebApplicationException ex) {
                assertTrue(ex.getErrorMessage().contains("Please create aliases without this format"));
            }
        });
    }

    @Test
    public void testAdminCuratorCheckAliasWithForbiddenFormat() {
        // An alias with an invalid prefix can be created by an admin or curator.
        testAdminCuratorCheckAliasWithForbiddenFormat(true, false);
        testAdminCuratorCheckAliasWithForbiddenFormat(false, true);
    }

    private void testAdminCuratorCheckAliasWithForbiddenFormat(boolean isAdmin, boolean isCurator) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        user.setCurator(isCurator);

        Set<String> zendodoDOIs = Set.of(ZENODO_DOI_ALIASES);
        AliasableResourceInterface.checkAliases(zendodoDOIs, user, true);
    }

    @Test
    public void testNonAdminCuratorCheckAliasAllowingForbiddenFormat() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Set<String> zendodoDOIs = Set.of(ZENODO_DOI_ALIASES);
        AliasableResourceInterface.checkAliases(zendodoDOIs, user, false);
    }

}
