/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Tag;

/**
 * Interface for how to grab data from a registry for docker containers.
 *
 * TODO: A pretty poor abstraction, there are quay.io data structures in here, one step at a time
 * 
 * @author dyuen
 */
public interface ImageRegistryInterface {

    /**
     * Get all tags for a given tool
     * 
     * @return a list of tags for image that this points to
     */
    List<Tag> getTags(Tool tool);

    /**
     * Get the list of namespaces and organizations that the user is associated to on Quay.io.
     *
     * @return list of namespaces
     */
    List<String> getNamespaces();

    /**
     * Get all containers from provided namespaces
     * 
     * @param namespaces
     * @return
     */
    List<Tool> getContainers(List<String> namespaces);

    /**
     * A bit of a misnomer, this not only gets a map of builds but populates the container with CWL-parsed info
     *
     * @param allRepos
     *            a list of images that gets modified with data from builds like data modified, size, etc.
     * @return map of path -> list of quay.io build data structure
     */
    Map<String, ArrayList<?>> getBuildMap(List<Tool> allRepos);
}
