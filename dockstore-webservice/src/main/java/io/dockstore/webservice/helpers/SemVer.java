package io.dockstore.webservice.helpers;

/**
 * This class is used to compare between versions defined using semantic versioning (SemVer)
 * @author gluu
 * @since 13/04/18
 */
public class SemVer implements Comparable<SemVer> {
    public final int[] numbers;

    /**
     * Converts a version into a SemVer object that can be used to compare between each other
     * @param version   The string version that uses semantic versioning (1.4.0-SNAPSHOT or 1.5.0)
     */
    public SemVer(String version) {
        final String split[] = version.split("\\-")[0].split("\\.");
        numbers = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            numbers[i] = Integer.parseInt(split[i]);
        }
    }

    /**
     * Compare between versions
     * @param another  The other SemVer to compare to
     * @return 0 if equal, 1 if higher, -1 if lower
     */
    @Override
    public int compareTo(SemVer another) {
        final int maxLength = Math.max(numbers.length, another.numbers.length);
        for (int i = 0; i < maxLength; i++) {
            final int left = i < numbers.length ? numbers[i] : 0;
            final int right = i < another.numbers.length ? another.numbers[i] : 0;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }
}
