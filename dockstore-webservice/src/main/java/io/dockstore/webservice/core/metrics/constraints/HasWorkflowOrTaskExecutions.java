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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the `HasWorkflowOrTaskExecutions` constraint annotation, which
 * checks that RunExecutionSubmission has either a workflow execution or a list of task executions.
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HasWorkflowOrTaskExecutionsValidator.class)
public @interface HasWorkflowOrTaskExecutions {
    String MUST_CONTAIN_WORKFLOW_OR_TASK_EXECUTIONS = "must contain a workflow execution or a list of task executions";

    String message() default MUST_CONTAIN_WORKFLOW_OR_TASK_EXECUTIONS;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
