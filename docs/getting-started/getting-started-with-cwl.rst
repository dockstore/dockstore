.. note:: This tutorial is a continuation of :doc:`Getting Started With Docker </getting-started/getting-started-with-docker>`. Please complete that tutorial prior to doing this one.

Getting Started with CWL
========================

Tutorial Goals
--------------

-  Learn about the `Common Workflow Language
   (CWL) <https://www.commonwl.org/user_guide/>`__
-  Create a basic CWL Tool which uses a Docker image
-  Run the Tool locally
-  Describe a sample parameterization of the Tool
-  Push the Tool onto GitHub

Describe Your Tool in CWL
-------------------------

Now that you have a git repository that includes a ``Dockerfile``, you
have tested it, and are satisfied that your tool works in Docker, the
next step is to create a `CWL tool definition
file <https://www.commonwl.org/user_guide/02-1st-example/>`__. This YAML
(Or JSON) file describes the inputs, outputs, and Docker image
dependencies for your tool.

It is recommended that you have the following minimum fields:

::

    doc: <description>
    id: <id>
    label: <label>

    cwlVersion: v1.0

    dct:creator:
      foaf:name: <name>

Again, we provide an example from the
`dockstore-tool-bamstats <https://github.com/CancerCollaboratory/dockstore-tool-bamstats>`__
repository:

::

    #!/usr/bin/env cwl-runner

    class: CommandLineTool
    id: "BAMStats"
    label: "BAMStats tool"
    cwlVersion: v1.0
    doc: |
        ![build_status](https://quay.io/repository/collaboratory/dockstore-tool-bamstats/status)
        A Docker container for the BAMStats command. See the [BAMStats](http://bamstats.sourceforge.net/) website for more information.

    dct:creator:
      "@id": "http://orcid.org/0000-0002-7681-6415"
      foaf:name: Brian O'Connor
      foaf:mbox: "mailto:briandoconnor@gmail.com"

    requirements:
      - class: DockerRequirement
        dockerPull: "quay.io/collaboratory/dockstore-tool-bamstats:1.25-6"

    hints:
      - class: ResourceRequirement
        coresMin: 1
        ramMin: 4092 #"the process requires at least 4G of RAM
        outdirMin: 512000

    inputs:
      mem_gb:
        type: int
        default: 4
        doc: "The memory, in GB, for the reporting tool"
        inputBinding:
          position: 1

      bam_input:
        type: File
        doc: "The BAM file used as input, it must be sorted."
        format: "http://edamontology.org/format_2572"
        inputBinding:
          position: 2

    outputs:
      bamstats_report:
        type: File
        format: "http://edamontology.org/format_3615"
        outputBinding:
          glob: bamstats_report.zip
        doc: "A zip file that contains the HTML report and various graphics."


    baseCommand: ["bash", "/usr/local/bin/bamstats"]

You can see this tool takes two inputs, a parameter to control memory
usage and a BAM file (binary sequence alignment file). It produces one
output, a zip file, that contains various HTML reports that BAMStats
creates.

The CWL is actually recognized and parsed by Dockstore (when we register
this later). By default it recognizes ``Dockstore.cwl`` but you can
customize this if you need to. One of the most important items below is
the `CWL
version <http://www.commonwl.org/v1.0/CommandLineTool.html#CWLVersion>`__.
You should label your CWL with the version you are using so that CWL
tools that cannot run this version will error out appropriately. Our
tools have been tested with v1.0.

::

    class: CommandLineTool
    id: "BAMStats"
    label: "BAMStats tool"
    cwlVersion: v1.0
    doc: |
            ![build_status](https://quay.io/repository/collaboratory/dockstore-tool-bamstats/status)
            A Docker container for the BAMStats command. See the [BAMStats](http://bamstats.sourceforge.net/) website for more information.

In the code above you can see how to have an extended doc (description)
which is quite useful.

::

    dct:creator:
      "@id": "http://orcid.org/0000-0002-7681-6415"
      foaf:name: Brian O'Connor
      foaf:mbox: "mailto:briandoconnor@gmail.com"

This section includes the tool author referenced by Dockstore. It is
open to your interpretation whether that is the person that registers
the tool, the person who made the Docker image, or the developer of the
original tool. I'm biased towards the person that registers the tool
since they are likely to be the primary contact when asking questions
about how the tool was setup.

You can register for an `ORCID <http://orcid.org/>`__ (a digital
identifer for researchers) or use an email address for your id.

::

    requirements:
      - class: DockerRequirement
        dockerPull: "quay.io/collaboratory/dockstore-tool-bamstats:1.25-6"

This section links the Docker image used for this CWL. Notice it's
exactly the same as the ``-t`` you used when building your image.

::

    hints:
      - class: ResourceRequirement
        coresMin: 1
        ramMin: 4092 # the process requires at least 4G of RAM
        outdirMin: 512000

This may or may not be honoured by the tool calling this CWL, but at
least it gives you a place to declare computational requirements.

::

    inputs:
      mem_gb:
        type: int
        default: 4
        doc: "The memory, in GB, for the reporting tool"
        inputBinding:
          position: 1

      bam_input:
        type: File
        doc: "The BAM file used as input, it must be sorted."
        format: "http://edamontology.org/format_2572"
        inputBinding:
          position: 2

This is one of the items from the inputs section. Notice a few things:

- The ``bam_input:`` matches with ``bam_input`` in the sample parameterization JSON (shown in the next section as ``sample_configs.local.json``).
- You can control the position of the variable.
- It can have a type (int or File here), and, for tools that require a prefix (``--prefix``) before a parameter you can use the ``prefix: key`` in the inputBindings section.
- I'm using the ``format`` field to specify a file format via the `EDAM <http://bioportal.bioontology.org/ontologies/EDAM>`__ ontology.

::

    outputs:
      bamstats_report:
        type: File
        format: "http://edamontology.org/format_3615"
        outputBinding:
          glob: bamstats_report.zip
        doc: "A zip file that contains the HTML report and various graphics."

Finally, the outputs section defines the output files. In this case, it
says in the current working directory there will be a file called
``bamstats_report.zip``. When running this tool with CWL tools the file
will be copied out of the container to a location you specify in your
parameter JSON file. We'll walk though an example in the next section.

Finally, the ``baseCommand`` is the actual command that will be
executed. In this case, it's the wrapper script I wrote for bamstats.

::

    baseCommand: ["bash", "/usr/local/bin/bamstats"]

The `CWL standard <http://www.commonwl.org/>`__ is continuing to evolve
and hopefully we will see new features, like support for `EDAM
ontology <http://edamontology.org/page>`__ terms, in future releases. In
the mean time, the `Gitter
chat <https://gitter.im/common-workflow-language/common-workflow-language>`__
is an active community to help drive the development of CWL in positive
directions and we recommend tool authors make their voices heard.

Testing Locally
---------------

So at this point, you've created a Docker-based tool and have described
how to call that tool using CWL. Let's test running the BAMStats using
the Dockstore command line and descriptor, rather than just directly
calling it via Docker. This will test that the CWL correctly describes
how to run your tool.

First thing I'll do is create a completely local dataset and JSON
parameterization file:

::

    $> wget ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/phase3/data/NA12878/alignment/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    # alternative location if the above URL doesn't work
    $> wget https://s3.amazonaws.com/oconnor-test-bucket/sample-data/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    $> mv NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam /tmp/

This downloads to my current directory and then moves to ``/tmp``. I
could choose another location, it really doesn't matter, but we need the
full path when dealing with the parameter JSON file. I'm using a sample
I checked in already: ``sample_configs.local.json``.

::

    {
        "bam_input": {
            "class": "File",
            "format": "http://edamontology.org/format_2572",
            "path": "/tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
        },
        "bamstats_report": {
            "class": "File",
            "path": "/tmp/bamstats_report.zip"
        }
    }

.. tip::  The Dockstore CLI can handle inputs with HTTPS, FTP, and S3

URLs but that's beyond the scope of this tutorial.

You can see in the above I give the full path to the input under
``bam_input`` and full path to the output ``bamstats_report``.

At this point, let's run the tool with our local inputs and outputs via
the JSON config file:

::

    $> dockstore tool launch --local-entry Dockstore.cwl --json sample_configs.local.json
       Creating directories for run of Dockstore launcher at: ./datastore//launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3
       Provisioning your input files to your local machine
       Downloading: #bam_input from /tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam into directory: /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9
       c9bf1-7094-4a21-b2a3-1b3ad330a0a3/inputs/78a05989-6978-45b0-b6e9-5f81e7aa34ad
       Calling out to cwltool to run your tool
       Executing: cwltool --enable-dev --non-strict --outdir /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/outputs/ --tmpdir-pre
       fix /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/tmp/ --tmp-outdir-prefix /media/dyuen/Data/large_volume/dockstore_tools
       /dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/working/ /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/Dockstore.cwl /media/dyuen/Data/large_vol
       ume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/workflow_params.json
       /usr/local/bin/cwltool 1.0.20170217172322
       Resolved '/media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/Dockstore.cwl' to 'file:///media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/Dockstore.cwl'
       [job Dockstore.cwl] /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/working/BHsHWq$ docker \
           run \
           -i \
           --volume=/media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/inputs/78a05989-6978-45b0-b6e9-5f81e7aa34ad/NA12878.chrom20.IL
       LUMINA.bwa.CEU.low_coverage.20121211.bam:/var/lib/cwl/stgc0a728c7-a8c0-44d3-be58-031fd656eb96/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam:ro \
           --volume=/media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/working/BHsHWq:/var/spool/cwl:rw \
           --volume=/media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/tmp/Z8umDA:/tmp:rw \
           --workdir=/var/spool/cwl \
           --read-only=true \
           --user=1001 \
           --rm \
           --env=TMPDIR=/tmp \
           --env=HOME=/var/spool/cwl \
           quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 \
           bash \
           /usr/local/bin/bamstats \
           4 \
           /var/lib/cwl/stgc0a728c7-a8c0-44d3-be58-031fd656eb96/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    ...
            [job Dockstore.cwl] completed success
            Final process status is success

    Saving copy of cwltool stdout to: /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/outputs/cwltool.stdout.txt
    Saving copy of cwltool stderr to: /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/outputs/cwltool.stderr.txt

    Provisioning your output files to their final destinations
    Uploading: #bamstats_report from /media/dyuen/Data/large_volume/dockstore_tools/dockstore-tool-bamstats/./datastore/launcher-9d9c9bf1-7094-4a21-b2a3-1b3ad330a0a3/outputs/bamstats_report.zip to : /tmp/bams
    tats_report.zip
    [##################################################] 100%

So that's a lot of information, but you can see the process was a
success. We get output from the command we ran and also see the file
being moved to the correct output location:

::

    $> ls -lth /tmp/bamstats_report.zip
    -rw-rw-r-- 1 ubuntu ubuntu 32K Jun 16 02:14 /tmp/bamstats_report.zip

The output looks fine, just what we'd expect.

So what's going on here? What's the Dockstore CLI doing? It can best be
summed up with this image:

.. figure:: /assets/images/docs/dockstore_lifecycle.png
   :alt: Lifecycle

   Lifecycle

The command line first provisions files. In our case, the files were
local so no provisioning was needed. But as the Tip above mentioned,
these can be various URLs. After provisioning the docker image is pulled
and ran via the ``cwltool`` command line. This uses the
``Dockerfile.cwl`` and parameterization JSON file
(``sample_configs.local.json``) to construct the underlying
``docker run`` command. Finally, the Dockstore CLI provisions files
back. In this case it's just a file copy to ``/tmp/bamstats_report.zip``
but it could copy the result to a destination in S3 for example.

.. tip::  You can use ``--debug`` to get much more information during
  this run, including the actual call to cwltool (which can be super
  helpful in debugging).

.. tip::  The ``dockstore`` CLI automatically creates a ``datastore``
  directory in the current working directory where you execute the command
  and uses it for inputs/outputs. It can get quite large depending on the
  tool/inputs/outputs being used. Plan accordingly, e.g. execute the
  dockstore CLI in a directory located on a partition with sufficient
  storage.

Adding a Test Parameter File
----------------------------

.. include:: adding-a-test-parameter-file.rst

Releasing on GitHub
-------------------

.. include:: releasing-on-github.rst

Building on Quay.io
-------------------

.. include:: building-on-quayio.rst

Next Steps
----------

Follow the :doc:`next tutorial </getting-started/register-on-dockstore/>` to create an
account on Dockstore and link third party services.

See Also
--------

- :doc:`WDL <getting-started-with-wdl>`
- :doc:`Nextflow <getting-started-with-nextflow>`
- :doc:`Language Support <../end-user-topics/language-support>`