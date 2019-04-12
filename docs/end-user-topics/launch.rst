Launching Tools and Workflows
=============================

Tutorial Goals
--------------

-  Launch a tool and a workflow using the Dockstore CLI

Dockstore CLI
-------------

The dockstore command-line includes basic tool and workflow launching
capability built on top of
`cwltool <https://github.com/common-workflow-language/cwltool>`__. The
Dockstore command-line also includes support for file provisioning via
`plugins <https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent>`__
which allow for the reading of input files and the upload of output
files from remote file systems. Support for HTTP and HTTPS is built-in.
Support for AWS S3 and `ICGC Score
client <https://github.com/dockstore/icgc-storage-client-plugin>`__ is
provided via plugins installed by default.

Launch Tools
~~~~~~~~~~~~

If you have followed the tutorial, you will have a tool registered on
Dockstore. You may want to test it out for your own work. For now you
can use the Dockstore command-line interface (CLI) to run several useful
commands:

1. create an empty "stub" JSON config file for entries in the Dockstore
   ``dockstore tool convert``
2. launch a tool locally ``dockstore tool launch``
3. automatically copy inputs from remote URLs if HTTP, FTP, S3 or other
   remote URLs are specified
4. call the ``cwltool`` command line to execute your tool using the CWL
   from the Dockstore and the JSON for inputs/outputs
5. if outputs are specified as remote URLs, copy the results to these
   locations
6. download tool descriptor files ``dockstore tool cwl`` and
   ``dockstore tool wdl``

Note that launching a CWL tool locally requires the cwltool to be
installed. Check `onboarding <https://dockstore.org/onboarding>`__ if
you have not already to ensure that your dependencies are correct.

An example of launching a tool, in this case a bamstats sample tool
follows:

::

    # make a runtime JSON template and fill in desired inputs, outputs, and other parameters
    $ dockstore tool convert entry2json --entry quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 > Dockstore.json
    $ vim Dockstore.json
    # note that the empty JSON config file has been filled with an input file retrieved via http
    $ cat Dockstore.json
    {
      "mem_gb": 4,
      "bam_input": {
        "path": "https://github.com/CancerCollaboratory/dockstore-tool-bamstats/raw/develop/rna.SRR948778.bam",
        "format": "http://edamontology.org/format_2572",
        "class": "File"
      },
      "bamstats_report": {
        "path": "/tmp/bamstats_report.zip",
        "class": "File"
      }
    }
    # run it locally with the Dockstore CLI
    $ dockstore tool launch --entry quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 --json Dockstore.json

This information is also provided in the "Launch With" section of every
tool.

Launch Workflows
~~~~~~~~~~~~~~~~

Launching CWL and WDL Workflows
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A parallel set of commands is available for workflows. ``convert``,
``wdl``, ``cwl``, and ``launch`` are all available under the
``dockstore workflow`` mode.

While launching tools and workflows locally is useful for testing, this
approach is not useful for processing a large amount of data in a
production environment. The next step is to take our Docker images,
described by CWL/WDL and run them in an environment that supports those
descriptors. For now, we can suggest taking a look at the environments
that currently support and are validated with CWL at
https://ci.commonwl.org/ and for WDL,
`Cromwell <https://github.com/broadinstitute/cromwell>`__.

For developers, you may also wish to look at our brief summary at `batch
services </batch-services>`__ and commercial solutions such as `Google
dsub <https://github.com/googlegenomics/task-submission-tools>`__ and
`AWS Batch <https://aws.amazon.com/batch/>`__.

Launching Nextflow Workflows
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Currently the Dockstore CLI does not support integration with the
Nextflow CLI. However, the Nextflow CLI offers many of the same benefits
as the Dockstore CLI.

All non-hosted workflows in Dockstore are associated with a Git
repository from GitHub, BitBucket, or GitLab. With the Nextflow CLI we
can launch a Dockstore workflow by using this Git repository information
through `pipeline
sharing <https://www.nextflow.io/docs/latest/sharing.html#pipeline-sharing>`__.

Say we have the workflow ``organization/my-workflow``. To launch, we
would run the following commands based on the code repository that the
workflow is stored on. See the link above for more advanced usage.

::

    # Run workflow from GitHub (--hub github is optional)
    nextflow run organization/my-workflow --hub github

    # Run workflow from BitBucket
    nextflow run organization/my-workflow --hub bitbucket

    # Run workflow from GitLab
    nextflow run organization/my-workflow --hub gitlab

Next Steps
----------

We also recommend looking at the best practices guide before creating
your first real tool/workflow. There are three descriptor languages
available on Dockstore. Follow the links for the language that you are
interested in.

- :doc:`Best practices for CWL <../advanced-topics/best-practices/best-practices>`
- :doc:`Best practices for WDL <../advanced-topics/best-practices/wdl-best-practices/>`
- :doc:`Best practices for Nextflow <../advanced-topics/best-practices/nfl-best-practices/>`

See Also
--------

-  :doc:`AWS Batch <../advanced-topics/aws-batch/>`
-  :doc:`Azure Batch <../advanced-topics/azure-batch/>`
-  :doc:`DNAstack Launch With <../end-user-topics/dnastack-launch-with/>`
-  :doc:`FireCloud Launch With <../end-user-topics/firecloud-launch-with/>`
-  :doc:`Terra Launch With <../end-user-topics/terra-launch-with/>`
