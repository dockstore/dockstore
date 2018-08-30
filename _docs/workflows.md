---
title: Getting Started with Dockstore Workflows
permalink: /docs/publisher-tutorials/workflows/
---
# Getting Started with Dockstore Workflows

This tutorial walks through the process of registering and sharing more complex workflows which are comprised of multiple tools (whether they are registered on Dockstore or not). Workflows as defined via the Dockstore are a composition of multiple tools, strung together in some sort of order (often a directed acyclic graph (DAG)). Workflows also are different from tools since they are not required to define their own environment, instead a workflow engine like [Arvados](https://arvados.org/) or [Cromwell](https://github.com/broadinstitute/cromwell) will provide the ability to execute a CWL or WDL workflow respectively.

## Comparison of Tools and Workflows Across Descriptor Languages

When Dockstore was first created, CWL was the only descriptor language we supported. It had a very clear distinction between a Tool and a Workflow. Once we started adding new descriptor languages like WDL and Nextflow, we had to define our own definitions of Tools and Workflows for those languages. Some people might wonder why we have these distinctions, but we think they are important.

| Language               | Tool          | Workflow   |
| ---------------------  | ------------- | ---------- | 
| CWL                    |  &#8226; Class: CommandLineTool             | &#8226; Class: Workflow           | 
| WDL                    |  &#8226; A single task<br> &#8226; A workflow section that runs the task<br> &#8226; An associated Docker image     | &#8226; >1 task<br> &#8226; A workflow section that connects the tasks           |  
| Nextflow               |  &#8226; N/A             |   &#8226; Any valid nextflow workflow         |
{: .table .table-striped .table-condensed}

## Create Your Workflow

The combination of light-weight Docker containers to run your tools and programmatic descriptors takes us part of the way there.
However, the next step is to chain together these containers in order to call tools in a particular sequence or in a particular pattern in order to create larger workflows.
Dockstore provides a few simple tools to share workflows, similar to how Dockstore shares command-line tools.

The steps to accomplish this task, at a high level, are:

0. create a new repository on GitHub, Bitbucket or GitLab
0. describe your workflow as either a [CWL workflow](http://www.commonwl.org/user_guide/) or a [WDL workflow](https://github.com/openwdl/wdl/blob/develop/SPEC.md#workflow-definition)
0. test your workflow using an environment that supports full CWL or WDL workflows
0. use the release process on GitHub, Bitbucket or GitLab to make distinct release tags, we like the  [HubFlow](https://datasift.github.io/gitflow/) process in our group for managing releases in git
0. create an entry on Dockstore and then publish it

An extremely simple example is available [here](https://dockstore.org/workflows/denis-yuen/dockstore-whalesay) and we will be working on more and better examples in the coming months.

## Create Workflow Stubs from GitHub, Bitbucket, and GitLab

The first step is to create a CWL or WDL workflow descriptor for your workflow and then check it into GitHub, Bitbucket or GitLab in a repo, we recommend the filename `Dockstore.cwl` at the root of your repository for simplicity but anything else with a consistent extension should work just as well. The details as to how to write a workflow are somewhat beyond the scope of this tutorial but we can recommend the [Introduction to the CWL](http://www.commonwl.org/user_guide/) and [Getting Started with WDL](https://github.com/openwdl/wdl/tree/master#getting-started-with-wdl).

You can also check in test parameter files into the same Git repo. These files are example input JSON (or YAML) files for running the given workflow. It should be easy for a user to run your workflow with the test parameter files in order to see an example of your workflow, so try and store any required files in the same Git repository or somewhere else where the files are likely to be present.

You can set a default test parameter file for a workflow, which defaults to `/test.json`. Whenever a new version is added, Dockstore will look for a file matching the default test parameter in the corresponding Git repository, and add it if it exists. Updating the default test parameter file path for an existing workflow will be propagated to any versions that have not been edited.

<!-- this following markdown link/anchor does not seem to work properly -->

The second step is to log in to the Dockstore. Make sure that you properly link Bitbucket and/or GitLab to your account if you are using workflows hosted on Bitbucket/Gitlab. After successfully linking your GitHub, Bitbucket and GitLab credentials (if applicable), you will be able to refresh your account on Dockstore and list your available repos on GitHub, Bitbucket and GitLab.

![My Workflows](/assets/images/docs/workflow_ui.png)

The above image shows the general structure of the UI that you should see after visiting "My Workflows." You can hit "Refresh All Workflows" in order to update information on published workflows or create new stubs for repos that you are about to publish. Workflows are a bit different from tools in that we create a stub entry for all your GitHub repos (and Bitbucket/GitLab repos). You can then promote these to full entries by refreshing, publishing and editing them. It is only at this point that the Dockstore will reach out and populate information such as available tags and source files. Workflows are handled differently from Tools in this regard since users may often have many more tags and repos on GitHub than Docker images on Quay.io.

## Register Your Workflow in Dockstore

Now that you have linked your credentials and refreshed, there should be one stub per repo that you are the owner of or have rights to see in GitHub.

### Quick Registration via the Web UI

In the authenticated Web UI, navigate to 'My Workflows' to begin managing workflows imported through your linked account(s). These pages will allow you to quickly register workflows that follow a particularly simple format (look below to manual registration for more complex formats). For quick registration, we look through your GitHub, Bitbucket and GitLab accounts and create a stub for each one. You can refresh and publish each repo that you identify as a real workflow in order to get, edit, and display additional information such as available versions.

In the above image, the left side menu is a list of all workflow repositories associated with the user, grouped lexicographically by namespace. Words encapsulated in parentheses denotes a custom toolname if provided. Detailed information and links for each Workflow is located on the 'Info' tab. The 'Labels' tab allows editing of keywords to be associated with a workflow for efficient searching and grouping. Settings such as the path to the CWL/WDL descriptor can be modified on a per-tag basis in the 'Versions' tab. The Descriptors and test parameter files may be viewed in the 'Files' tab, by the Version tag (corresponding to a Git tag/branch).

Just like with Tools, a workflow is not visible on the public 'Workflows' listing unless it is published. To publish a container, click the yellow 'Publish' button in the top-right corner.

#### Manual Registration of Workflows

In certain cases, you may wish to register workflows in a different source code structure, especially when working with complex project structures. For example, if you want to register two workflows from the same repository.

Workflows can be registered manually from the 'My Workflows' page by pressing the 'Register Workflow' button at the bottom of the right side bar, or any of the '+' buttons in each accordion namespace group. A modal will appear as below:

![Register Workflow Manual](/assets/images/docs/register_workflow_manual.png)

The Source Code Repository field must be filled out and is in the format `namespace/name` (the two paths may differ). The Workflow (descriptor) path and test parameter path are relative to the root of the Source Code Repository (and must begin with '/'), these will be the default locations to find their corresponding files, unless specified otherwise in the tags. The toolname is an optional 'suffix' appended to the Dockstore path, it allows for two workflows to share the same Git paths; the toolname uniquely distinguishes image repositories in Dockstore.

Upon successful submission and publishing of the workflow, a resynchronization call will be made to fetch all available data from the given sources.

The user may then browse to the 'Versions' tab of the new container, where tags (corresponding to GitHub/Bitbucket/GitLab tag names) may be edited.

The fields in the form should correspond to the actual values on GitHub/Bitbucket/GitLab in order for the information to be useful to other users. Selecting `Hidden` will prevent the tag from appearing in the public listing of tags for the workflow.

### CLI Client

The `dockstore` command line has several options. When working with workflows, use `dockstore workflow` to get a full list of options. We recommend you first use `dockstore workflow refresh` to ensure the latest GitHub, Bitbucket, and GitLab information is indexed properly.

You can then use `dockstore workflow publish` to see the list of available workflows you can register with Dockstore and then register them. This is for you to publish workflows with the simplest structure. For now, use manual registration if your workflow has a different structure. The key is that workflows you wish to (simply) publish have the following qualities:

0. public
0. at least one valid tag. In order to be valid, a tag has to:
    * have the reference be linked a corresponding `Dockstore.cwl` or `Dockstore.wdl` hosted at the root of the repository

The `dockstore workflow manual_publish` command can be used to manually register a workflow on GitHub, Bitbucket or GitLab. Its usage is outlined in the manual_publish help menu.

## Find Other Workflows

You can find tools on the Dockstore website or also through the `dockstore workflow search` command line option.

## Next Steps

Learn about [Hosted Tools and Workflows](/docs/publisher-tutorials/hosted-tools-and-workflows/).
