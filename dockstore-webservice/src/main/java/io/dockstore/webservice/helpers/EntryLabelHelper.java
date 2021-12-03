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

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.jdbi.LabelDAO;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.http.HttpStatus;

/**
 * This class contains code for interacting with labels for all types of entries.
 * <p>
 * Created by dyuen on 10/03/16.
 */
public class EntryLabelHelper<T extends Entry> {
    private LabelDAO labelDAO;

    public EntryLabelHelper(LabelDAO lDAO) {
        this.labelDAO = lDAO;
    }

    public T updateLabels(T entry, String labelStrings) {

        if (labelStrings.length() == 0) {
            entry.setLabels(new TreeSet<>());
        } else {
            Set<String> labelStringSet = new HashSet<>(Arrays.asList(labelStrings.toLowerCase().split("\\s*,\\s*")));
            final String labelStringPattern = "^[a-zA-Z0-9]++(-[a-zA-Z0-9]++)*+$";

            // This matches the restriction on labels to 255 characters
            // if this is changed then the java object/mapped db schema needs to be changed
            final int labelMaxLength = 255;
            SortedSet<Label> labels = new TreeSet<>();
            for (final String labelString : labelStringSet) {
                if (labelString.length() <= labelMaxLength && labelString.matches(labelStringPattern)) {
                    Label label = labelDAO.findByLabelValue(labelString);
                    if (label != null) {
                        labels.add(label);
                    } else {
                        label = new Label();
                        label.setValue(labelString);
                        long id = labelDAO.create(label);
                        labels.add(labelDAO.findById(id));
                    }
                } else {
                    throw new CustomWebApplicationException("Invalid label format", HttpStatus.SC_BAD_REQUEST);
                }
            }
            entry.setLabels(labels);
        }
        return entry;
    }
}
