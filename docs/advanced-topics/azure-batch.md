# Azure Batch

[Azure Batch](https://azure.microsoft.com/en-us/services/batch/) has been created to provide a simple way of running containers and simple commands on Azure without you having to closely manage the underlying VM infrastructure (although a knowledge of the underlying infrastructure will always be useful). While Azure Batch does not have an understanding of CWL like a full-on workflow engine, it does provide a very simple way to run a large number of Dockstore tools at scale.

Azure Batch also provides a client-side tool called [Batch Shipyard](https://github.com/Azure/batch-shipyard) which provides a number of features including a simple command-line interface for submitting batch jobs.

Of course, keep in mind that if you have a knowledge of CWL and/or do not need the Dockstore command-line to do file provisioning, you can decompose the underlying command-line invocation for the tool and use that as the command for your jobs, gaining a bit of performance. This tutorial focuses on using cwltool and using the Dockstore command-line to provide an experience that is more akin to running Dockstore or cwltool [on the command-line](/launch#dockstore-cli) out of the box.

1. Run through Azure Shipyard's [Linux Installation Guide](https://github.com/Azure/batch-shipyard/blob/master/docs/01-batch-shipyard-installation.md#step-2a-linux-run-the-installsh-script) and then the [Quickstart](https://github.com/Azure/batch-shipyard/blob/master/docs/02-batch-shipyard-quickstart.md) guide with one of the sample tools such as Torch-CPU.
1. With the shipyyard CLI setup, get the md5sum sample recipes from GitHub
    ```
    $ git clone https://github.com/dockstore/batch_wrapper.git
    $ cd batch_wrapper/azure/
    ```
1. Fill out your `config.json`, `credentials.json`, and `jobs.json` in `config.dockstore.md5sum`. If you have trouble finding your access keys, take a look at this [article](https://docs.microsoft.com/en-us/azure/batch/batch-account-create-portal#view-batch-account-properties). In `jobs.json` note that we use AWS keys to provision or save the final output files. You will also need to modify the parameter json file `md5sum.s3.json` to reflect the location of your S3 bucket.
1. Create a compute pool. Note that this pool is not setup to automatically resize. You may also need to pick a larger VM size with a larger dataset.
    ```
    $ ./shipyard pool add --configdir config.dockstore.md5sum
    ```
1. Submit the job and watch the output (this should take roughly a minute if the pool already exists)
    ```
    $ ./shipyard jobs add --configdir config.dockstore.md5sum --tail stdout.txt
    2017-05-24 14:19:21.543 INFO - Adding job dockstorejob to pool dockstore
    2017-05-24 14:19:21.989 INFO - uploading file /tmp/tmp7lgz7_j7 as 'shipyardtaskrf-dockstorejob/dockertask-00012.shipyard.envlist'
    2017-05-24 14:19:22.027 DEBUG - submitting 1 tasks (0 -> 0) to job dockstorejob
    2017-05-24 14:19:22.090 INFO - submitted all 1 tasks to job dockstorejob
    2017-05-24 14:19:22.090 DEBUG - attempting to stream file stdout.txt from job=dockstorejob task=dockertask-00012
    Creating directories for run of Dockstore launcher at: ./datastore//launcher-e849c691-cc47-4bfa-a443-b8830794ae0a
    Provisioning your input files to your local machine
    Downloading: #input_file from https://raw.githubusercontent.com/briandoconnor/dockstore-tool-md5sum/master/md5sum.input into directory: /mnt/batch/tasks/workitems/dockstorejob/job-1/dockertask-00012/wd/./datastore/launcher-e849c691-cc47-4bfa-a443-b8830794ae0a/inputs/ce735ade-8c46-4736-a7d8-2fc0cb7d2e87
    [##################################################] 100%
    Calling out to cwltool to run your tool
    ...
    Final process status is success

    Saving copy of cwltool stdout to: /mnt/batch/tasks/workitems/dockstorejob/job-1/dockertask-00012/wd/./datastore/launcher-e849c691-cc47-4bfa-a443-b8830794ae0a/outputs/cwltool.stdout.txt
    Saving copy of cwltool stderr to: /mnt/batch/tasks/workitems/dockstorejob/job-1/dockertask-00012/wd/./datastore/launcher-e849c691-cc47-4bfa-a443-b8830794ae0a/outputs/cwltool.stderr.txt

    Provisioning your output files to their final destinations
    Uploading: #output_file from /mnt/batch/tasks/workitems/dockstorejob/job-1/dockertask-00012/wd/./datastore/launcher-e849c691-cc47-4bfa-a443-b8830794ae0a/outputs/md5sum.txt to : s3://dockstore.temp/md5sum.txt
    Calling on plugin io.dockstore.provision.S3Plugin$S3Provision to provision to s3://dockstore.temp/md5sum.txt
    [##################################################] 100%
    ```
1. You can repeat the process with `config.dockstore.bwa` which is a more realistic bioinformatics workflow from the [PCAWG project](http://icgc.org/working-pancancer-data-aws) and takes roughly seven hours.

## See Also

* [AWS Batch](aws-batch/)
* [DNAstack Launch With](/end-user-topics/dnastack-launch-with/)
* [FireCloud Launch With](/end-user-topics/firecloud-launch-with/)
* [Terra Launch With](/end-user-topics/terra-launch-with/)
