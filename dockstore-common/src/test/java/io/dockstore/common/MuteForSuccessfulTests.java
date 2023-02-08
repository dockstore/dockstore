package io.dockstore.common;

import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import uk.org.webcompere.systemstubs.stream.SystemStreamBase;

public class MuteForSuccessfulTests implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        try {
            Object test = context.getRequiredTestInstance();
            // Iterate over the fields of the test object.
            for (Field field: test.getClass().getDeclaredFields()) {
                // Skip any field that doesn't extend SystemStreamBase,
                // thus avoiding a potential security/access exception on an irrelevant private field.
                if (SystemStreamBase.class.isAssignableFrom(field.getType())) {
                    // Make the "stream" field accessible if it is not already.
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    // Extract the "stream" field value.
                    if (field.get(test) instanceof SystemStreamBase stream) {
                        // Get the captured text and output it.
                        String text = stream.getText();
                        if (text != null) {
                            System.out.print(text);
                            System.out.flush();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println(String.format("Exception in MuteForSuccessfulTests extension: %s", ex.getMessage()));
        }
    }
}
