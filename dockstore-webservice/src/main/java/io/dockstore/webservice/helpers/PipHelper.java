package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gluu
 * @since 13/04/18
 */
public final class PipHelper {
    private PipHelper() { }

    /**
     * Figures out which pip requirements file to resolve to since not every clientVersion changes the pip requirements.
     * This function should be modified every time a new pip requirements file is added.
     * @param semVerString  The Dockstore client version
     * @return              The most recently changed pip requirements file to the Dockstore client version (can be older, but not newer)
     */
    public static String convertSemVerToAvailableVersion(String semVerString) {
        if (semVerString == null) {
            semVerString = "9001.9001.9001";
        }
        SemVer semVer = new SemVer(semVerString);
        if (semVer.compareTo(new SemVer("1.5.0")) >= 0) {
            return "1.5.0";
        } else {
            return "1.4.0";
        }
    }

    /**
     * Converts a pip requirements file's contents into a map that can be used as a json
     * @param pipRequirementsString     The pip requirements file's contents
     * @return                          A map equivalent to the file's contents
     */
    public static Map<String, String> convertPipRequirementsStringToMap(String pipRequirementsString) {
        String[] lines = pipRequirementsString.split(System.getProperty("line.separator"));
        List<String> pipDeps = new ArrayList(Arrays.asList(lines));
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
}
