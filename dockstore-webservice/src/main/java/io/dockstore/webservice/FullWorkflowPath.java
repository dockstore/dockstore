/*
 * Copyright 2022 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.SourceControlConverter;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Workflow is broken into three different subclasses: BioWorkflow, Service, and AppTool. All three are in separate tables in our database and there is a unique
 * constraint within each table to prevent duplicate names. However, we want names to be unique across workflows and apptools, so this is being added to keep track
 * of the full path names within a single table and have the constraint be enforced at the database level.
 *
 * @author Natalie
 */

@Entity
@Table(name = "fullworkflowpath")
public class FullWorkflowPath implements Serializable {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    @ApiModelProperty(value = "Implementation specific ID for the full workflow path in this web service.")
    private long id;

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "This is the name of the workflow, not needed when only one workflow in a repo")
    private String workflowName;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git organization for the workflow", required = true)
    private String organization;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git repository name", required = true)
    private String repository;

    @Column(nullable = false, columnDefinition = "text")
    @ApiModelProperty(value = "This is a specific source control provider like github, bitbucket, gitlab, etc.", required = true, dataType = "string")
    @Convert(converter = SourceControlConverter.class)
    private SourceControl sourceControl;

}
