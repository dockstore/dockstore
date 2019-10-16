package io.dockstore.webservice.resources;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import org.junit.Test;

import static org.junit.Assert.fail;

public class AliasableResourceInterfaceTest {
    String[] ZENODO_DOI_ALIASES = {"10.5281/zenodo.6094", "10.5281/zenodo.60943", "10.5281/zenodo.6094355",
            "10.5281-zenodo.6094", "10.5281-zenodo.60943", "10.5281-zenodo.6094355"};


    @Test
    public void testAddAliasWithBadPrefix() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES).forEach(invalidPrefix -> {
            String invalidAlias = invalidPrefix + "_some_random_alias_suffix";
            // Make sure the alias is valid
            // If it is not acceptable then an exception is generated
            boolean throwsError = false;
            try {
                AliasableResourceInterface.checkAliases(Collections.singleton(invalidPrefix + "_some_random_alias_suffix"),
                        user, true);
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
        // An alias with an invalid prefix can be created by an admin or curator.
        testAdminCuratorAddAliasWithBadPrefix(true, false);
        testAdminCuratorAddAliasWithBadPrefix(false, true);
    }

    public void testAdminCuratorAddAliasWithBadPrefix(boolean isAdmin, boolean isCurator) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        user.setCurator(isCurator);

        Set<String> alisesWithInvalidPrefixes = Arrays.stream(AliasableResourceInterface.INVALID_PREFIXES)
                .map(invalidPrefix -> invalidPrefix + "_some_random_alias_suffix").collect(Collectors.toSet());

        AliasableResourceInterface.checkAliases(alisesWithInvalidPrefixes, user, true);
    }

    @Test
    public void testAddAliasWithForbiddenFormat() {
        User user = new User();
        user.setIsAdmin(false);
        user.setCurator(false);

        Arrays.stream(ZENODO_DOI_ALIASES).forEach(zenodoDOI -> {
            // Make sure the alias is valid
            // If it is not acceptable then an exception is generated
            boolean throwsError = false;
            try {

                AliasableResourceInterface.checkAliases(Collections.singleton(zenodoDOI), user, true);
            } catch (CustomWebApplicationException ex) {
                throwsError = true;
            }

            if (!throwsError) {
                fail("The alias " + zenodoDOI + " has a forbidden format but was reported to be OK.");
            }
        });
    }

    @Test
    public void testAdminCuratorAddAliasWithForbiddenFormat() {
        // An alias with an invalid prefix can be created by an admin or curator.
        testAdminCuratorAddAliasWithBadPrefix(true, false);
        testAdminCuratorAddAliasWithBadPrefix(false, true);
    }

    public void testAdminCuratorAddAliasWithForbiddenFormat(boolean isAdmin, boolean isCurator) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        user.setCurator(isCurator);

        Set<String> zendodoDOIs = Set.of(ZENODO_DOI_ALIASES);
        AliasableResourceInterface.checkAliases(zendodoDOIs, user, true);
    }
}
