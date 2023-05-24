package io.dockstore.common;

import com.github.zafarkhaja.semver.Version;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author gluu
 * @since 13/04/18
 */
public final class PipHelper {
    public static final String DEV_SEM_VER = "development-build";
    // https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
    // OPENAPI_SEM_VER_STRING is only used for the openapi schema pattern. Do not use it for pattern matching because it does not mitigate against ReDos attacks.
    // The openapi schema pattern must be a valid regular expression according to the Ecma-262 dialect, which does not support possessive quantifiers.
    public static final String OPENAPI_SEM_VER_STRING = "(^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$)|(^" + DEV_SEM_VER + "$)";
    // SEM_VER_STRING regex is modified with possessive quantifiers to mitigate against ReDoS attacks. Use this for pattern matching.
    public static final String SEM_VER_STRING = OPENAPI_SEM_VER_STRING.replace("*", "*+");
    public static final Pattern SEM_VER_PATTERN = Pattern.compile(SEM_VER_STRING);

    private PipHelper() { }

    /**
     * Figures out which pip requirements file to resolve to since not every clientVersion changes the pip requirements.
     * This function should be modified every time a new pip requirements file is added.
     * @param semVerString  The Dockstore client version
     * @return              The most recently changed pip requirements file to the Dockstore client version
     */
    public static String convertSemVerToAvailableVersion(String semVerString) {
        if (semVerString == null || DEV_SEM_VER.equals(semVerString)) {
            semVerString = "9001.9001.9001";
        }
        Version semVer = Version.valueOf(semVerString);
        if (semVer.greaterThanOrEqualTo(Version.valueOf("1.14.0"))) {
            return "1.14.0";
        } else {
            return "1.13.0";
        }
    }

    /**
     * Converts a pip requirements file's contents into a map that can be used as a json
     * @param pipRequirementsString     The pip requirements file's contents
     * @return                          A map equivalent to the file's contents
     */
    public static Map<String, String> convertPipRequirementsStringToMap(String pipRequirementsString) {
        String[] lines = pipRequirementsString.split(System.getProperty("line.separator"));
        List<String> pipDeps = new ArrayList<>(Arrays.asList(lines));
        Map<String, String> pipDepMap = new HashMap<>();
        pipDeps.forEach(pipDep -> {
            String[] split = pipDep.split("==");
            String key = split[0];
            String mapValue;
            if (split.length != 2) {
                mapValue = "any";
            } else {
                mapValue = split[1];
            }
            pipDepMap.put(key, mapValue);
        });
        return pipDepMap;
    }

    /**
     * Validates a string that represents a semantic version.
     * @param semVer String representing a semantic version.
     * @return true if the semantic version is valid else false.
     */
    public static boolean validateSemVer(String semVer) {
        return SEM_VER_PATTERN.matcher(semVer).matches();
    }
}
