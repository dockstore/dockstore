package io.dockstore.common;

/**
 * For tests that use a lot of memory. On Circle CI, unit tests were frequently failing
 * due to a Java VM 137 exit code, which is the VM running out of memory.
 * Tests using this annotation will be run via a separate Maven profile, in a new VM,
 * enabling both sets of test to finish without running out of memory.
 */
public interface MemoryIntensiveTest {
}
