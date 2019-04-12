.. note:: This tutorial is a continuation of :doc:`Getting Started with Dockstore Workflows <dockstore-workflows>`. Please complete the tutorial prior to doing this one.

Hosted Tools and Workflows
==========================

Tutorial Goals
--------------

-  Compare Hosted Workflows and Remote Workflows
-  Register a Hosted CWL Workflow on Dockstore

Introduction to Hosted Tools and Workflows
------------------------------------------

A hosted tool or workflow is simply an entry where instead of files
being stored in a Git repository they are stored within Dockstore. It is
useful if you don't use GitHub, or if you want to take advantage of our
sharing features to share your work with a limited audience. For this
tutorial we will only look at hosted workflows. The process is the same
with hosted tools, the only difference is that hosted tools also have
Dockerfiles, and do not yet support sharing via permissions.

.. important:: 
  For hosted tools we do not store the Docker image in our own registry.

Adding a Hosted Workflow
------------------------

In this example we are going to add a simple CWL workflow to Dockstore
as a hosted workflow.

To add a hosted workflow do the following:

- Go to `myworkflows <https://dockstore.org/my-workflows>`__
- Click the (+) button to add a new workflow
- Select ``Create and save CWL, WDL, or Nextflow on Dockstore.org``
- Select thedescriptor type (CWL for this example)
- Choose a workflow name
- Click Register Workflow!

Once you have registered the workflow, a new workflow is added with the
path dockstore.org/{username}/{workflow name}.

Adding a version
~~~~~~~~~~~~~~~~

You now have a hosted workflow created, however it has no files! To add
a new version we must add the minimum set of files required for a valid
workflow. For the case of CWL that is a ``Dockstore.cwl`` file.

Click on the ``Edit Files`` button to enter edit mode, then click on
``Add File``. You should see a file automatically added called
``Dockstore.cwl``. Now we are going to populate it with the following
content:

::

    #!/usr/bin/env cwl-runner

    cwlVersion: v1.0
    class: Workflow
    inputs:
      inp: File
      ex: string

    outputs:
      classout:
        type: File
        outputSource: compile/classfile

    steps:
      untar:
        run: tar-param.cwl
        in:
          tarfile: inp
          extractfile: ex
        out: [example_out]

      compile:
        run: arguments.cwl
        in:
          src: untar/example_out
        out: [classfile]

This workflow imports two files. We must add two more files using the
``Add File`` button and then populate them accordingly.

``tar-param.cwl``

::

    cwlVersion: v1.0
    class: CommandLineTool
    baseCommand: [tar, xf]
    inputs:
      tarfile:
        type: File
        inputBinding:
          position: 1
      extractfile:
        type: string
        inputBinding:
          position: 2
    outputs:
      example_out:
        type: File
        outputBinding:
          glob: $(inputs.extractfile)

``arguments.cwl``

::

    cwlVersion: v1.0
    class: CommandLineTool
    label: Example trivial wrapper for Java 7 compiler
    hints:
      DockerRequirement:
        dockerPull: java:7-jdk
    baseCommand: javac
    arguments: ["-d", $(runtime.outdir)]
    inputs:
      src:
        type: File
        inputBinding:
          position: 1
    outputs:
      classfile:
        type: File
        outputBinding:
          glob: "*.class"

Now press ``Save as New Version`` and we will have successfully added a
version!

Any time you edit the files and save your changes, a new version will be
added. The version number is auto incremented by 1 each time. Like
regular workflows, you can also hide specific versions from appearing to
the public. You can also delete versions, though we recommend simply
hiding them to preserve history. Deleting should be limited to simple
things like typos or missing comments.

Next Steps
----------

Find out how to launch your tools and workflows at :doc:`Launching Tools and
Workflows <../end-user-topics/launch/>`.

Advanced Topics
---------------

Are you interested in learning advanced topics? See our :doc:`advanced
topics <../advanced-topics/advanced-topics>` page to get the most out of Dockstore.
