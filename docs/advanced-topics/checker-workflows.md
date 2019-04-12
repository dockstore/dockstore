# Checker Workflows

## Tutorial Goals
* Learn about checker workflows
* Add a checker workflow to an existing tool
* Update a checker workflow
* Launch a checker workflow

## Overview of Checker Workflows
Checker workflows are additional workflows you can associate with a tool or workflow. The purpose of them is to ensure that a tool or workflow, given some inputs, produces the expected outputs on a platform different from the one where you are developing.

In the near future, checker workflows can be used for iterations of the GA4GH-DREAM challenge. In the long-term, they can picked up and run by the automated GA4GH workflow "testbed" to test your workflows across a variety of workflow platforms. See this [presentation](https://docs.google.com/presentation/d/1VXdReGYXayzO7Jr-9XaLHNv6Wt46CwfvkfFDR8OEgJM/edit?usp=sharing) for more details and contact the GA4GH Cloud Work Stream for more information on contributing to the workflow testbed or related APIs.

Below is a visual overview of how a checker workflow looks.

![Checker Workflow Layout](/assets/images/docs/checker-workflow.png)

The term "entry" will be used as a generic term to refer to both tools and workflows.

The entry that a checker workflow is testing will be referred to as an original tool/workflow/entry.

For this tutorial we will be registering a checker workflow to test an original tool that calculates the MD5sum of a file.

The relevant tools and workflows can be found in the following Git repository:
<!-- warning, a bare link will look like it creates a hyperlink in the githbu editor, but is compiled to plain text by Jekyll --> 
[https://github.com/dockstore-testing/md5sum-checker](https://github.com/dockstore-testing/md5sum-checker)

#### Quick overview of structure
Like regular workflows, a checker workflow can describe an example input from an input parameter file. The checker workflow can either use the input parameter file for the original entry, or it can define its own. The second case is useful when the validation tool/workflow has some extra parameters not required by the original entry.

For our example the second case is used. The original tool has the input parameter file [/md5sum-input-cwl.json](https://github.com/dockstore-testing/md5sum-checker/blob/master/md5sum/md5sum-input-cwl.json). This is the file that runs a particular example with the original tool.
```
{
  "input_file": {
        "class": "File",
        "path": "md5sum.input"
    }
}
```

The checker workflow has the input parameter file [/checker-input-cwl.json](https://github.com/dockstore-testing/md5sum-checker/blob/master/checker-input-cwl.json). This is the file that we would pass to the checker workflow to ensure that our original tool is working properly when we run it with the input file mentioned above. Again, in some cases this file could be the same as the one for the original tool parameter file, though not in this case.
```
{
  "input_file": {
        "class": "File",
        "path": "md5sum.input"
    },
    "expected_md5": "00579a00e3e7fa0674428ac7049423e2"
}
```

Notice that the checker parameter file has the same content as the original parameter file, in addition to having a checker specific parameter.

One point of confusion is that a checker workflow contains a validation tool/workflow. The validation tool/workflow is what does the bulk of the validation. It is responsible for ensuring that the results of the original entry match expected results. The checker workflow refers to the workflow that connects the original entry with the validation tool/workflow, so that it can be run as one workflow.


#### Output of checker workflow
To ensure that checking a checker workflow's output can be automated, it is important that the checker workflow produce consistent exit codes. We require using an exit code of 0 for success and an exit code of not 0 for failures.

We also recommend producing the following two output files containing the stdout and stderr respectively:
* log.stdout
* log.stderr

#### Note on CLI usage
For existing dockstore commands (tools and workflows), entry refers to the path of a specific tool or workflow. For checker workflows, entry refers to the path of the original entry. It does not refer to the checker workflow's path.

## Adding a checker workflow
Currently, you can add checker workflows to existing tools and workflows through the UI and CLI

### From the UI
Lets add a checker workflow for the tool described by [/md5sum/md5sum-tool.cwl](https://github.com/dockstore-testing/md5sum-checker/blob/master/md5sum/md5sum-tool.cwl) in the git repository. I already have the tool properly setup on Dockstore. For this tutorial it is assumed that you are familiar with the process for setting up tools and workflows on Dockstore.

The first step is to find the tool under the my tools page. In the info tab there is an option to add a checker workflow. Click on the add button.

![Checker Workflow Add](/assets/images/docs/checker-workflow-add.png)

When registering a checker workflow, you need the following fields:
* Default checker workflow path (path to main descriptor of the checker workflow)
* Default test parameter file (if not given will copy over from original entry)
* Descriptor type (CWL or WDL) when original entry is a tool

![Checker Workflow Register](/assets/images/docs/checker-workflow-register.png)

Once a checker workflow has been added, you can view it by going to the info tab of the original entry. Where there used to be an add button, there is now the view button. View will take you back to your checker workflow page.

### From the CLI
Run the command `dockstore checker --help` to see all available checker workflow commands.
For now we are interested in the add command.

Using our example checker workflow, we would run the following:

`dockstore checker add --entry quay.io/natalieeo/md5sum-checker --descriptor-type cwl --descriptor-path /checker-workflow-wrapping-tool.cwl --input-parameter-path /checker-input-cwl.json`

This will add the checker workflow defined by [/checker-workflow-wrapping-tool.cwl](https://github.com/dockstore-testing/md5sum-checker/blob/master/checker-workflow-wrapping-tool.cwl) to the entry `quay.io/natalieeo/md5sum-checker`.

The descriptor type will default to 'CWL' if none is provided.
The default input parameter path will default to the default input parameter path of the original entry.

## Updating a checker workflow

### From the UI
Updating a checker workflow and associated versions can be done the same way as with normal workflows. The only difference is that to get to the correct page in My Workflows you must go through the original tool or workflow, in My Tools and My Workflows respectively.

### From the CLI
Updating a checker workflow can be done the same way as updating a normal workflow, though there are fewer options.

You can update the default test parameter path and the default descriptor path. Run `dockstore checker update --help` for more information.

Lets update the default descriptor path in our example to a new value.
`dockstore checker update --entry quay.io/natalieeo/md5sum-checker --default-descriptor-path /checker-workflow-wrapping-tool.cwl`

This will update the default descriptor path for the checker workflow. Although in this example, the path is already properly set.

Updating versions of a checker workflow is also quite similar to updating versions of a workflow, but again, there are fewer options. Run `dockstore checker update_version --help` for more information.

We can update the master version of our example checker workflow to be hidden by running
`dockstore checker update_version --entry quay.io/natalieeo/md5sum-checker --name master --hidden true`

## Adding/Removing test input parameter files for a version

### From the UI
Updating the test input parameter files associated with a checker workflow version can be done the same way as with normal workflows. The only difference is that to get to the correct page in My Workflows you must go through the original tool or workflow, in My Tools and My Workflows respectively.

### From the CLI
Like most commands, adding/removing test input parameter files to a checker workflow version can be done in a similar fashion to normal workflows. No functionality is lost for this command. Run `dockstore checker test_parameter --help` for more information.

`dockstore checker test_parameter --entry quay.io/natalieeo/md5sum-checker --version master --add /checker-input-cwl.json`

This will add the test parameter file [/checker-input-cwl.json](https://github.com/dockstore-testing/md5sum-checker/blob/master/checker-input-cwl.json) to the master version of the checker workflow. Though in our example we already added it when we added the checker workflow, so nothing will happen.

## Launching a checker workflow

### From the CLI
Launching a checker workflow from the CLI should feel very familiar if you have launched tools or workflows on the CLI. You launch it the same as any other entry, however you use the checker mode.

Below is an example of launching a checker workflow for our md5sum example.

`dockstore checker launch --entry quay.io/natalieeo/md5sum-checker:master --json test.json`

In this example, test.json is a local version of the following file:
[/checker-input-cwl.json](https://github.com/dockstore-testing/md5sum-checker/blob/master/checker-input-cwl.json)

We also need a local version of the file we are calculating the md5sum for:
[/md5sum.input](https://github.com/dockstore-testing/md5sum-checker/blob/master/md5sum.input)

## Downloading all relevant files for a checker workflow
It can be useful to have all relevant files for a checker workflow locally. This can be done with the download feature.

### From the CLI
The command for this is very simple. Again note that the entry is for the original entry, and not the checker workflow.

`dockstore checker download --entry quay.io/natalieeo/md5sum-checker --version master`

This will download the descriptor and any secondary descriptors, while maintaining the correct directory structure.

## For Advanced Users
You can interact with checker workflows using TRS. See
[Checker Workflows and the TRS](checker-workflow-trs/) for more information.
