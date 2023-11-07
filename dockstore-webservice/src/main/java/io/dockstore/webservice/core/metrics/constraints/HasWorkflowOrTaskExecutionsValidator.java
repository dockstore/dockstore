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

package io.dockstore.webservice.core.metrics.constraints;

import io.dockstore.webservice.core.metrics.RunExecutionSubmission;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that RunExecutionSubmission has a workflow execution or a list of task executions.
 */
public class HasWorkflowOrTaskExecutionsValidator implements ConstraintValidator<HasWorkflowOrTaskExecutions, RunExecutionSubmission> {

    @Override
    public boolean isValid(final RunExecutionSubmission runExecutionSubmission, final ConstraintValidatorContext context) {
        return runExecutionSubmission.getWorkflowExecution() != null || (runExecutionSubmission.getTaskExecutions() != null && !runExecutionSubmission.getTaskExecutions().isEmpty());
    }
}
