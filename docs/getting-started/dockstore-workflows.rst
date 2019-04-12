.. note::
    This tutorial is a continuation of :doc:`Getting Started with Dockstore Tools </getting-started/dockstore-tools>`.
    Please complete the tutorial prior to doing this one.

Dockstore Workflows
===================

Tutorial Goals
--------------

-  Register a workflow on Dockstore
-  Learn the differences between tools and workflows across Descriptor
   Languages
-  Publish your workflow

This tutorial walks through the process of registering and sharing more
complex workflows which are comprised of multiple tools (whether they
are registered on Dockstore or not). Workflows as defined via the
Dockstore are a composition of multiple tools, strung together in some
sort of order (often a directed acyclic graph (DAG)). Workflows also are
different from tools since they are not required to define their own
environment, instead a workflow engine like
`Arvados <https://arvados.org/>`__ or
`Cromwell <https://github.com/broadinstitute/cromwell>`__ will provide
the ability to execute a CWL or WDL workflow respectively.

Comparison of Tools and Workflows Across Descriptor Languages
-------------------------------------------------------------

When Dockstore was created, CWL was the first descriptor language we
supported. It had a very clear distinction between a Tool and a
Workflow. Descriptor languages like WDL and Nextflow are less clear
about this distinction so we briefly describe our working definitions
below:

+------------------------+------------------------------------------+-----------------------------------------------+
| Language               | Tool                                     | Workflow                                      |
+========================+==========================================+===============================================+
| CWL                    | - Class: CommandLineTool                 | - Class: Workflow                             |
+------------------------+------------------------------------------+-----------------------------------------------+
| WDL                    | - A single task with Docker image        | - >1 task                                     |
|                        | - A workflow section that runs the task  | - A workflow section that connects the tasks  |
|                        | - An associated Docker image             |                                               |
+------------------------+------------------------------------------+-----------------------------------------------+
| Nextflow               | - N/A                                    | - Any valid nextflow workflow                 |
|                        |                                          |                                               |
+------------------------+------------------------------------------+-----------------------------------------------+


Create Your Workflow
--------------------

The combination of light-weight Docker containers to run your tools and
programmatic descriptors takes us part of the way there. However, the
next step is to chain together these containers in order to call tools
in a particular sequence or pattern in order to create larger workflows.
Dockstore provides a few simple tools to share workflows, similar to how
Dockstore shares command-line tools.

The steps to accomplish this task, at a high level, are:

1. Create a new repository on GitHub, Bitbucket or GitLab
2. Describe your workflow as either a
   `CWL <http://www.commonwl.org/user_guide/>`__,
   `WDL <https://github.com/openwdl/wdl/blob/develop/SPEC.md#workflow-definition>`__
   or a `Nextflow <https://www.nextflow.io/>`__ workflow
3. Test your workflow using an environment that supports full CWL, WDL
   or Nextflow workflows
4. Use the release process on GitHub, Bitbucket or GitLab to make
   distinct release tags. We like the
   `HubFlow <https://datasift.github.io/gitflow/>`__ process in our
   group for managing releases in git
5. Create an entry on Dockstore and then publish it

Create Workflow Stubs from GitHub, Bitbucket, and GitLab
--------------------------------------------------------

The first step is to create a CWL or WDL workflow descriptor for your
workflow and then check it into GitHub, Bitbucket or GitLab in a repo.
We recommend the filename ``Dockstore.cwl`` at the root of your
repository for simplicity, but anything else with a consistent extension
should work just as well. The details as to how to write a workflow are
somewhat beyond the scope of this tutorial, but we can recommend the
`Introduction to the CWL <http://www.commonwl.org/user_guide/>`__ and
`Getting Started with
WDL <https://github.com/openwdl/wdl/tree/master#getting-started-with-wdl>`__.

You can also check in test parameter files into the same Git repo. These
files are example input JSON (or YAML) files for running the given
workflow. It should be easy for a user to run your workflow with the
test parameter files in order to see an example of your workflow. So try
to store any required files in the same Git repository or somewhere else
where the files are likely to be present.

You can set a default test parameter file for a workflow, which defaults
to ``/test.json``. Whenever a new version is added, Dockstore will look
for a file matching the default test parameter in the corresponding Git
repository, and add it if it exists. Updating the default test parameter
file path for an existing workflow will be propagated to any versions
that have not been edited.

.. raw:: html

   <!-- this following markdown link/anchor does not seem to work properly -->

The second step is to log in to the Dockstore. Make sure that you
properly link Bitbucket and/or GitLab to your account if you are using
workflows hosted on Bitbucket/Gitlab. After successfully linking your
GitHub, Bitbucket and GitLab credentials (if applicable), you will be
able to refresh your account on Dockstore and list your available repos
on GitHub, Bitbucket and GitLab.

.. figure:: /assets/images/docs/workflow_ui.png
   :alt: My Workflows

   My Workflows

The above image shows the general structure of the UI that you should
see after visiting "My Workflows." You can hit "Refresh All Workflows"
in order to update information on published workflows or create new
stubs for repos that you are about to publish. Workflows are a bit
different from tools in that we create a stub entry for all your GitHub
repos (and Bitbucket/GitLab repos). You can then promote these to full
entries by refreshing, publishing and editing them. It is only at this
point that the Dockstore will reach out and populate information such as
available tags and source files. Workflows are handled differently from
Tools in this regard since users may often have many more tags and repos
on GitHub than Docker images on Quay.io.

Register Your Workflow in Dockstore
-----------------------------------

Now that you have linked your credentials and refreshed, there should be
one stub per repo that you are the owner of or have rights to see in
GitHub.

Quick Registration via the Web UI
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In the authenticated Web UI, navigate to 'My Workflows' to begin
managing workflows imported through your linked account(s). These pages
will allow you to quickly register workflows that follow a particularly
simple format (look below to manual registration for more complex
formats). For quick registration, we look through your GitHub, Bitbucket
and GitLab accounts and create a stub for each one. You can refresh and
publish each repo that you identify as a real workflow in order to get,
edit, and display additional information such as available versions.

In the above image, the left side menu is a list of all workflow
repositories associated with the user, grouped lexicographically by
namespace. Words encapsulated in parentheses denotes a custom toolname
if provided. Detailed information and links for each Workflow are
located on the 'Info' tab. Settings, such as the path to the CWL/WDL
descriptor, can be modified on a per-tag basis in the 'Versions' tab.
The Descriptors and test parameter files may be viewed in the 'Files'
tab, by the Version tag (corresponding to a Git tag/branch). Finally,
'Manage labels' (located above the tabs) allows you to add/edit keywords
that you want to be associated with a workflow for efficient searching
and grouping.

Just like with Tools, a workflow is not visible on the public
'Workflows' listing unless it is published. To publish a container,
click the blue 'Publish' button in the top-right corner.

Manual Registration of Workflows
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In certain cases, you may wish to register workflows in a different
source code structure, especially when working with complex project
structures. For example, if you want to register two workflows from the
same repository.

Workflows can be registered manually from the 'My Workflows' page by
pressing the 'Register Workflow' button at the bottom of the right side
bar. A modal will appear as below:

.. figure:: /assets/images/docs/register_workflow_manual.png
   :alt: Register Workflow Manual

   Register Workflow Manual

The first option allows you to register a workflow from an existing
third party repository. The second option allows you to write your
workflow files and store them directly on Dockstore.org. You can read
more about hosted workflows :doc:`here </getting-started/hosted-tools-and-workflows/>`, but
for now, let's select the first option. Clicking 'Next' will bring up
the following modal:

.. figure:: /assets/images/docs/register_workflow_manual2.png
   :alt: Register Workflow Manual2

   Register Workflow Manual2

Source Code Provider allows you to choose between GitHub, BitBucket, and
GitLab (your respective accounts for these third party repositories need
to be linked to your Dockstore account). The Source Code Repository
field must be filled out and is in the format ``namespace/name`` (the
two paths may differ). The Workflow (descriptor) path and test parameter
path are relative to the root of the Source Code Repository (and must
begin with '/'). These will be the default locations to find their
corresponding files, unless specified otherwise in the tags. The
Workflow Name is an optional 'suffix' appended to the Dockstore path. It
allows for two workflows to share the same Git paths; the Workflow Name
uniquely distinguishes workflow repositories in Dockstore.

Upon successful submission and publishing of the workflow, a
resynchronization call will be made to fetch all available data from the
given sources.

The user may then browse to the 'Versions' tab of the new container,
where tags (corresponding to GitHub/Bitbucket/GitLab tag names) may be
edited.

The fields in the form should correspond to the actual values on
GitHub/Bitbucket/GitLab in order for the information to be useful to
other users. Selecting ``Hidden`` will prevent the tag from appearing in
the public listing of tags for the workflow.

CLI Client
~~~~~~~~~~

The ``dockstore`` command line has several options. When working with
workflows, use ``dockstore workflow`` to get a full list of options. We
recommend you first use ``dockstore workflow refresh`` to ensure the
latest GitHub, Bitbucket, and GitLab information is indexed properly.

You can then use ``dockstore workflow publish`` to see the list of
available workflows you can register with Dockstore and then register
them. This is for you to publish workflows with the simplest structure.
For now, use manual registration if your workflow has a different
structure. The key is that workflows you wish to (simply) publish have
the following qualities:

1. public
2. at least one valid tag. In order to be valid, a tag has to:

   -  have the reference be linked a corresponding ``Dockstore.cwl`` or
      ``Dockstore.wdl`` hosted at the root of the repository

The ``dockstore workflow manual_publish`` command can be used to
manually register a workflow on GitHub, Bitbucket or GitLab. Its usage
is outlined in the manual\_publish help menu.

Find Other Workflows
--------------------

You can find tools on the Dockstore website or also through the
``dockstore workflow search`` command line option.

Next Steps
----------

You may not want to store your files directly with a service like
GitHub. Perhaps you want your descriptor files to not be public. The
solution is to use :doc:`Hosted Tools and
Workflows </getting-started/hosted-tools-and-workflows/>`.
