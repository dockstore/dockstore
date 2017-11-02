/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.foobar;

/**
 * Dummy class to satisfy coveralls-maven-plugin (does not like empty modules)
 * Created by dyuen on 26/05/16.
 */
public final class Dummy {
    private Dummy() {
        // hide constructors for utility classes
    }

    public static void main(String[] args) {
        System.out.println();
    }
}
