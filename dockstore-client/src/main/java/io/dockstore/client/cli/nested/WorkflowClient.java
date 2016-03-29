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

package io.dockstore.client.cli.nested;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

import io.dockstore.client.cli.Client;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Body1;
import io.swagger.client.model.Label;
import io.swagger.client.model.Workflow;

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;

/**
 * This stub will eventually implement all operations on the CLI that are
 * specific to workflows.
 *
 * @author dyuen
 */
public class WorkflowClient extends AbstractEntryClient {

    private final WorkflowsApi workflowsApi;

    public WorkflowClient(WorkflowsApi workflowApi) {
        this.workflowsApi = workflowApi;
    }

    @Override
    public String getEntryType() {
        return "Workflow";
    }

    @Override
    public void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet) {
        // Try and update the labels for the given container
        try {
            Workflow container = workflowsApi.getWorkflowByPath(entryPath);
            long containerId = container.getId();
            List<Label> existingLabels = container.getLabels();
            Set<String> newLabelSet = new HashSet<>();

            // TODO: this following part is repetitive, see if we can refactor along with ToolClient

            // Get existing labels and store in a List
            for (Label existingLabel : existingLabels) {
                newLabelSet.add(existingLabel.getValue());
            }

            // Add new labels to the List of labels
            for (String add : addsSet) {
                final String label = add.toLowerCase();
                newLabelSet.add(label);
            }
            // Remove labels from the list of labels
            for (String remove : removesSet) {
                final String label = remove.toLowerCase();
                newLabelSet.remove(label);
            }

            String combinedLabelString = Joiner.on(",").join(newLabelSet);

            Workflow updatedContainer = workflowsApi.updateLabels(containerId, combinedLabelString, new Body1());

            List<Label> newLabels = updatedContainer.getLabels();
            if (newLabels.size() > 0) {
                out("The container now has the following labels:");
                for (Label newLabel : newLabels) {
                    out(newLabel.getValue());
                }
            } else {
                out("The container has no labels.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void printClientSpecificHelp() {
        /** do nothing for now */
    }

    @Override
    public void handleInfo(String entryPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void refreshAllEntries() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void refreshTargetEntry(String toolpath) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handleDescriptor(String descriptorType, String entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handleListNonpublishedEntries() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void publish(List<String> args) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handleSearch(String pattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handleList() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        return false;
    }
}
