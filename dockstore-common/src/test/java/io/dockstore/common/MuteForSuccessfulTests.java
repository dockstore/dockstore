/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.common;

import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import uk.org.webcompere.systemstubs.stream.SystemStreamBase;

/**
 * Implements a Junit 5 extension that will print the captured output
 * from any SystemStubs "stream" test fields (any field with a type that
 * is a subclass of SystemStreamBase, such as SystemOut and SystemErr)
 * to stdout for any failed test.
 *
 * Note that the default behavior, without this extension, is for
 * SystemOut/SystemErr to capture the output, but not print it.
 */
public class MuteForSuccessfulTests implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {

        try {
            // Get the object that represents the failed test.
            Object test = context.getRequiredTestInstance();

            // Climb the class hierarchy, starting at the leaf subclass.
            for (Class<?> klass = test.getClass(); klass != null; klass = klass.getSuperclass()) {

                // Iterate over the declared fields of the class.
                for (Field field: klass.getDeclaredFields()) {

                    // Skip any fields that do not extend SystemStreamBase.
                    if (SystemStreamBase.class.isAssignableFrom(field.getType())) {

                        // Extract the value of the "stream" field from the test object.
                        field.trySetAccessible();
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
            }
        } catch (Exception ex) {
            System.err.println(String.format("Exception in MuteForSuccessfulTests extension: %s", ex.getMessage()));
        }
    }
}
