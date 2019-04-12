.. role:: raw-latex(raw)
   :format: latex
..

.. note:: This tutorial is a continuation of :doc:`Getting Started With Docker <getting-started-with-docker>`. Please complete that tutorial prior to doing this one.

Getting Started with WDL
========================

Tutorial Goals
--------------

-  Learn about the `Workflow Description Language
   (WDL) <http://www.openwdl.org/>`__
-  Create a basic WDL Tool which uses a Docker image
-  Run the Tool locally
-  Describe a sample parameterization of the Tool
-  Push the Tool onto GitHub

Describe Your Tool in WDL
-------------------------

Besides CWL, you can also describe tools via the `WDL
language <https://software.broadinstitute.org/wdl/documentation/>`__.
WDL does not directly have the concept of a Tool built in to the
language like CWL. Instead, we define a tool as a one task WDL workflow,
where the task has an associated Docker image.

We provide a hello world example as follows:

::

    task hello {
      String name

      command {
        echo 'hello ${name}!'
      }
      output {
        File response = stdout()
      }
      runtime {
       docker: 'ubuntu:latest'
     }
    }

    workflow test {
      call hello
    }

The runtime section of a task allows you to use a docker image to run
the task in. In this example we use the basic `Ubuntu
image <https://hub.docker.com/_/ubuntu/>`__. This image should match the
Dockerfile that you register on Dockstore alongside your WDL descriptor
files.

Again, we provide an example from the
`dockstore-tool-bamstats <https://github.com/CancerCollaboratory/dockstore-tool-bamstats/blob/develop/Dockstore.wdl>`__
repository:

::

    task bamstats {
        File bam_input
        Int mem_gb

        command {
            bash /usr/local/bin/bamstats ${mem_gb} ${bam_input}
        }

        output {
            File bamstats_report = "bamstats_report.zip"
        }

        runtime {
            docker: "quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0" 
            memory: mem_gb + "GB"
        }

        meta {
            author: "Andrew Duncan"
        }
    }

    workflow bamstatsWorkflow {
        File bam_input
        Int mem_gb  
        call bamstats { input: bam_input=bam_input, mem_gb=mem_gb }
    }

Let us break it down piece by piece.

You'll notice that there are two main sections of the file. First is a
task section where we define the task level inputs and outputs of a
given step, along with the runtime. Next, there is a workflow section
where we define workflow level inputs and outputs, and the calling of
the task.

At the top of the task section we define two inputs: the input bam file
and the amount of memory in GB to use to run the task. This looks very
similar to variable declaration in most programming languages.

::

    File bam_input
    Int mem_gb

Next is the command section. This specifies what command we want to run
on the command line. We can also pass the command parameters based on
the inputs described above. Here we pass the amount of memory to use and
the input BAM file to a script from the
quay.io/collaboratory/dockstore-tool-bamstats:1.25-6\_1.0 docker image.

::

    command {
        bash /usr/local/bin/bamstats ${mem_gb} ${bam_input}
    }

The output section defines the expected output for the task. Here the
output is a ZIP file containing the results of the script.

::

    output {
        File bamstats_report = "bamstats_report.zip"
    }

The runtime section is very important to Dockstore. It is here where we
define what Docker image to use to run the task in. We also define how
much memory the Docker container should use.

::

    runtime {
        docker: "quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0" 
        memory: mem_gb + "GB"
    }

Finally, we have a metadata section where we can store key value pairs.
It is free-form, so we could put anything here. Dockstore is able to
pick up author, email, and description if they are defined here. All
metadata values must be a single-line string.

The description field can be used to add documentation and Dockstore
will treat the string as markdown, rendering accordingly. When writing a
description in markdown that requires newlines, specify the newlines
with :raw-latex:`\n `or specify a blank line with
:raw-latex:`\n`:raw-latex:`\n`.

Below we show an example metadata section and how it will display on
your workflow's landing page:

::

    meta {
        author: "Andrew Duncan"
        email: "andrew@foobar.com"
        description: "## Bamstats \n This is the Bamstats workflow.\n\n Adding documentation improves clarity."
    }

.. figure:: /assets/images/docs/wdl_meta_example.png
   :alt: wdl\_metadata

   wdl\_metadata

The workflow section here consists of two main parts. The first section
is an input section, where we define the input BAM file and the memory
to use.

::

    File bam_input
    Int mem_gb

Finally there is the call section where we actually call the tasks.
Without this section our tool will not do anything. In this section we
call the bamstats tool, and pass it the two required parameters.

::

    call bamstats { input: bam_input=bam_input, mem_gb=mem_gb }

Testing Locally
---------------

So at this point, you’ve created a Docker-based tool and have described
how to call that tool using WDL. Let’s test running the BAMStats using
the Dockstore command line and descriptor rather than just directly
calling it via Docker. This will test that the WDL correctly describes
how to run your tool.

First thing I’ll do is create a completely local dataset and JSON
parameterization file:

::

    $> wget ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/phase3/data/NA12878/alignment/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    # alternative location if the above URL doesn't work
    $> wget https://s3.amazonaws.com/oconnor-test-bucket/sample-data/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam

This downloads to my current directory. I could choose another location,
it really doesn't matter. I'm using a sample I checked in already:
``test.wdl.json``.

::

    {
      "bamstatsWorkflow.bam_input": "NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam",
      "bamstatsWorkflow.mem_gb": "4"
    }

.. tip::  the Dockstore CLI can handle inputs with HTTPS, FTP, GS, and S3
    URLs but that's beyond the scope of this tutorial.

You can see in the above I give the relative path to the input under
``bam_input`` and the memory in GB that I want to use for the task.

At this point, let's run the tool with our local inputs and outputs via
the JSON config file:

::

    $> dockstore tool launch --local-entry Dockstore.wdl --json test.wdl.json 
    Creating directories for run of Dockstore launcher in current working directory: /home/aduncan/Documents/dockstore-tool-bamstats
    Provisioning your input files to your local machine
    Downloading: bamstatsWorkflow.bam_input from NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam to: /home/aduncan/Documents/dockstore-tool-bamstats/cromwell-input/aca839a6-92c4-4234-bc6d-460bcfe6f4d6/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    Calling out to Cromwell to run your workflow
    java -jar /home/aduncan/.dockstore/libraries/cromwell-30.2.jar run /home/aduncan/Documents/dockstore-tool-bamstats/Dockstore.wdl --inputs /tmp/foo7282099563694004806json
    Cromwell exit code: 0
    Cromwell stdout:
    [2018-08-30 14:23:40,47] [info] Running with database db.url = jdbc:hsqldb:mem:93932e57-4451-41b9-8d64-c550c1f8afc6;shutdown=false;hsqldb.tx=mvcc   [2018-08-30 14:23:43,78] [info] Running migration RenameWorkflowOptionsInMetadata with a read batch size of 100000 and a write batch size of 100000 [2018-08-30 14:23:43,78] [info] [RenameWorkflowOptionsInMetadata] 100%  [2018-08-30 14:23:43,84] [info] Running with database db.url = jdbc:hsqldb:mem:0424c7e8-e6fd-41dc-a21a-5daa245e038c;shutdown=false;hsqldb.tx=mvcc   [2018-08-30 14:23:44,05] [info] Slf4jLogger started [2018-08-30 14:23:44,15] [info] Metadata summary refreshing every 2 seconds.    [2018-08-30 14:23:44,16] [info] Starting health monitor with the following checks: DockerHub, Engine Database   [2018-08-30 14:23:44,17] [info] WriteMetadataActor configured to write to the database with batch size 200 and flush rate 5 seconds.    [2018-08-30 14:23:44,18] [info] CallCacheWriteActor configured to write to the database with batch size 100 and flush rate 3 seconds.   [2018-08-30 14:23:44,64] [info] SingleWorkflowRunnerActor: Submitting workflow  [2018-08-30 14:23:44,67] [info] Workflow 4d24ebd1-5151-4b07-82d7-272b184fd0eb submitted.    [2018-08-30 14:23:44,67] [info] SingleWorkflowRunnerActor: Workflow submitted 4d24ebd1-5151-4b07-82d7-272b184fd0eb  [2018-08-30 14:23:44,67] [info] 1 new workflows fetched [2018-08-30 14:23:44,67] [info] WorkflowManagerActor Starting workflow 4d24ebd1-5151-4b07-82d7-272b184fd0eb [2018-08-30 14:23:44,68] [info] WorkflowManagerActor Successfully started WorkflowActor-4d24ebd1-5151-4b07-82d7-272b184fd0eb    [2018-08-30 14:23:44,68] [info] Retrieved 1 workflows from the WorkflowStoreActor   [2018-08-30 14:23:45,18] [info] MaterializeWorkflowDescriptorActor [4d24ebd1]: Call-to-Backend assignments: bamstatsWorkflow.bamstats -> Local  [2018-08-30 14:23:45,25] [warn] Local [4d24ebd1]: Key/s [memory] is/are not supported by backend. Unsupported attributes will not be part of job executions.    [2018-08-30 14:23:45,25] [warn] Couldn't find a suitable DSN, defaulting to a Noop one. [2018-08-30 14:23:45,26] [info] Using noop to send events.  [2018-08-30 14:23:47,30] [info] WorkflowExecutionActor-4d24ebd1-5151-4b07-82d7-272b184fd0eb [4d24ebd1]: Starting calls: bamstatsWorkflow.bamstats:NA:1  [2018-08-30 14:23:47,88] [warn] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: Unrecognized runtime attribute keys: memory    [2018-08-30 14:23:47,92] [info] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: bash /usr/local/bin/bamstats 4 /cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/inputs/home/aduncan/Documents/dockstore-tool-bamstats/cromwell-input/aca839a6-92c4-4234-bc6d-460bcfe6f4d6/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam   [2018-08-30 14:23:47,93] [info] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: executing: docker run \      --cidfile /home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/docker_cid \    --rm -i \    \      --entrypoint /bin/bash \    -v /home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats:/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats \  quay.io/collaboratory/dockstore-tool-bamstats@sha256:8472101666cda2a29be9abe8184ec2c7cae4360b75e712706921476b6b537679 /cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/script    [2018-08-30 14:23:47,95] [info] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: job id: 27953  [2018-08-30 14:23:47,95] [info] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: Status change from - to WaitingForReturnCodeFile   [2018-08-30 14:25:08,39] [info] BackgroundConfigAsyncJobExecutionActor [4d24ebd1bamstatsWorkflow.bamstats:NA:1]: Status change from WaitingForReturnCodeFile to Done    [2018-08-30 14:25:10,30] [info] WorkflowExecutionActor-4d24ebd1-5151-4b07-82d7-272b184fd0eb [4d24ebd1]: Workflow bamstatsWorkflow complete. Final Outputs:  {     "bamstatsWorkflow.bamstats.bamstats_report": "/home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/bamstats_report.zip"  }   [2018-08-30 14:25:10,32] [info] WorkflowManagerActor WorkflowActor-4d24ebd1-5151-4b07-82d7-272b184fd0eb is in a terminal state: WorkflowSucceededState  [2018-08-30 14:25:27,76] [info] SingleWorkflowRunnerActor workflow finished with status 'Succeeded'.    {     "outputs": {      "bamstatsWorkflow.bamstats.bamstats_report": "/home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/bamstats_report.zip"      },      "id": "4d24ebd1-5151-4b07-82d7-272b184fd0eb"  }   [2018-08-30 14:25:27,81] [info] Message [cromwell.core.actor.StreamActorHelper$StreamFailed] without sender to Actor[akka://cromwell-system/deadLetters] was not delivered. [1] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.   [2018-08-30 14:25:27,81] [info] Message [cromwell.core.actor.StreamActorHelper$StreamFailed] without sender to Actor[akka://cromwell-system/deadLetters] was not delivered. [2] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.   [2018-08-30 14:25:27,84] [info] Automatic shutdown of the async connection  [2018-08-30 14:25:27,84] [info] Gracefully shutdown sentry threads. [2018-08-30 14:25:27,84] [info] Shutdown finished.  
    Cromwell stderr:

    Saving copy of Cromwell stdout to: /home/aduncan/Documents/dockstore-tool-bamstats/Cromwell.stdout.txt
    Saving copy of Cromwell stderr to: /home/aduncan/Documents/dockstore-tool-bamstats/Cromwell.stderr.txt
    Output files left in place

So that’s a lot of information but you can see the process was a
success. The output is kind of hard to parse, but look for the following
text

::

    Workflow bamstatsWorkflow complete. Final Outputs:  {
            "bamstatsWorkflow.bamstats.bamstats_report": "/home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/bamstats_report.zip"
            }

The final output can be found at
``/home/aduncan/Documents/dockstore-tool-bamstats/cromwell-executions/bamstatsWorkflow/4d24ebd1-5151-4b07-82d7-272b184fd0eb/call-bamstats/execution/bamstats_report.zip``.

So what's going on here? What's the Dockstore CLI doing? It can best be
summed up with this image:

.. figure:: /assets/images/docs/dockstore_lifecycle.png
   :alt: Lifecycle

   Lifecycle

The command line first provisions input files. In our case, the files
were local so no provisioning was needed. But as the tip above
mentioned, these can be various URLs pointing to remote files. After
provisioning the docker image is pulled and ran via the ``Cromwell``
command line. This uses the ``Dockstore.wdl`` and parameterization JSON
file (``test.wdl.json``) to construct the underlying ``docker run``
command. Finally, the Dockstore CLI provisions files back.

.. tip::  You can use ``--debug`` to get much more information during
    this run, including the actual call to Cromwell (which can be super
    helpful in debugging):

::

    java -jar /home/aduncan/.dockstore/libraries/cromwell-30.2.jar run /home/aduncan/Documents/dockstore-tool-bamstats/Dockstore.wdl --inputs /tmp/foo7282099563694004806json

.. tip::  The ``dockstore`` CLI automatically create a ``datastore``
    directory in the current working directory where you execute the command
    and uses it for inputs/outputs. It can get quite large depending on the
    tool/inputs/outputs being used. Plan accordingly e.g. execute the
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

Follow the :doc:`next tutorial <register-on-dockstore/>` to create an
account on Dockstore and link third party services.

See Also
--------

-  :doc:`CWL <getting-started-with-cwl/>`
-  :doc:`Nextflow <getting-started-with-nextflow/>`
-  :doc:`Language Support <../end-user-topics/language-support/>`
