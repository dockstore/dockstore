---
title: Launch
permalink: /docs/user-tutorials/launch/
---
<div class="alert alert-info">
This tutorial is a continuation of <a href="/docs/publisher-tutorials/hosted-tools-and-workflows/">Hosted Tools and Workflows</a>. Please complete the tutorial prior to doing this one.
</div>

# Launching Tools and Workflows
## Tutorial Goals
* Launch a tool and a workflow using the Dockstore CLI

## Dockstore CLI

The dockstore command-line includes basic tool and workflow launching capability built on top of [cwltool](https://github.com/common-workflow-language/cwltool). The Dockstore command-line also includes support for file provisioning via [plugins](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent) which allow for the reading of input files and the upload of output files from remote file systems. Support for http and https is built-in. Support for AWS S3 and [icgc-storage client](https://github.com/dockstore/icgc-storage-client-plugin) is provided via plugins installed by default.

### Launch Tools

If you have followed the tutorial, you will have a tool registered on Dockstore. You may want to test it out for your own work. For now you can use the Dockstore command-line interface (CLI) to run several useful commands:

0. create an empty "stub" JSON config file for entries in the Dockstore `dockstore tool convert`
0. launch a tool locally `dockstore tool launch`
  0. automatically copy inputs from remote URLs if HTTP, FTP, S3 or other remote URLs are specified
  0. call the `cwltool` command line to execute your tool using the CWL from the Dockstore and the JSON for inputs/outputs
  0. if outputs are specified as remote URLs, copy the results to these locations
0. download tool descriptor files `dockstore tool cwl` and `dockstore tool wdl`

Note that launching a CWL tool locally requires the cwltool to be installed. Check [onboarding](https://dockstore.org/onboarding) if you have not already to ensure that your dependencies are correct.

An example of launching a tool, in this case a bamstats sample tool follows:

```
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
```

This information is also provided in the "Launch With" section of every tool.

### Launch Workflows

A parallel set of commands is available for workflows. `convert`, `wdl`, `cwl`, and `launch` are all available under the `dockstore workflow` mode.

## Batch Services

Dockstore tools and workflows can also be run through a number of online services that we're going to loosely call "commercial batch services." These services share the following characteristics: they spin up the underlying infrastructure and run commands, often in Docker containers, while freeing you from running the batch computing software yourself. While not having any understanding of CWL, these services can be used naively to run tools and workflows, and in a more sophisticated way to implement a CWL-compatible workflow engine.  

### AWS Batch

[AWS Batch](https://aws.amazon.com/batch/) is built by Amazon Web Services. Look [here](/docs/publisher-tutorials/aws-batch) for a tutorial on how to run a few sample tools via AWS.

### Azure Batch

[Azure Batch](https://azure.microsoft.com/en-us/services/batch/) and the associated [batch-shipyard](https://github.com/Azure/batch-shipyard) is built by Microsoft. Look [here](/docs/publisher-tutorials/azure-batch) for a tutorial on how to run a few sample tools via Azure.

### Google Pipelines

Google Pipeline and [Google dsub](https://github.com/googlegenomics/dsub) are also worth a look. In particular, both [Google Genomics Pipelines](https://cloud.google.com/genomics/v1alpha2/pipelines) and [dsub](https://cloud.google.com/genomics/v1alpha2/dsub) provide tutorials on how to run  (Dockstore!) tools if you have some knowledge on how to construct the command-line for a tool yourself.

## Consonance

Consonance pre-dates Dockstore and was the framework used to run much of the data analysis for the [PCAWG](https://dcc.icgc.org/pcawg#!%2Fmutations) project by running [Seqware](https://seqware.github.io/) workflows. Documentation for this incarnation of Dockstore can be found at [Working with PanCancer Data on AWS](http://icgc.org/working-pancancer-data-aws) and [ICGC on AWS](https://aws.amazon.com/public-datasets/icgc/).

Consonance has subsequently been updated to run Dockstore tools and has also been adopted at the [UCSC Genomics Institute](https://github.com/BD2KGenomics/dcc-ops) for this purpose. Also using cwltool under-the-hood to provide CWL compatibility, Consonance provides DIY open-source support for provisioning AWS VMs and starting CWL tasks. We recommend having some knowledge of AWS EC2 before attempting this route.

Consonance's strategy is to provision either on-demand VMs or spot priced VMs depending on cost and delegates runs of CWL tools to these provisioned VMs with one tool executing per VM. A Java-based web service and RabbitMQ provide for communication between workers and the launcher while an Ansible playbook is used to setup workers for execution.

### Usage

1. Look at the [Consonance](https://github.com/Consonance/consonance) repo and in particular, the [Docker compose based](https://github.com/Consonance/consonance/tree/develop/container-admin) setup instructions to setup the environment
2. Once logged into the client
```
consonance run --tool-dockstore-id quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 --run-descriptor Dockstore.json --flavour <AWS instance-type>
```

## Notifications
The Dockstore CLI has the ability to provide notifications via an HTTP post to a user-defined endpoint for the following steps:
- The beginning of input files provisioning
- The beginning of tool/workflow execution
- The beginning of output files provisioning
- Final launch completion

Additionally, it will also provide notifications when any of these steps have failed.

### Usage
- Define a webhook URL in the Dockstore config file with the "notifications" property like:
```
token: iamafakedockstoretoken
server-url: https://dockstore.org:8443
notifications: https://hooks.slack.com/services/aaa/bbb/ccc
```
- UUID can be generated or user-defined uuid in the dockstore launch command like:
```bash
dockstore tool launch --local-entry Dockstore.cwl --json test.json --uuid fakeUUID
```
- An HTTP post with a JSON payload will be sent to the url defined earlier that looks like:
```json
{
  "text": "someTextBasedOnMilestoneAndStatus",
  "username": "your linux username",
  "platform": "Dockstore CLI 1.4",
  "uuid": "someUserDefinedOrGeneratedUUID"
}
```

### Notes
- To disable notifications, simply remove the webhook URL from the Dockstore config file
- If the UUID is generated, the generated UUID will be displayed in beginning of the launch stdout

While launching tools and workflows locally is useful for testing, this approach is not useful for processing a large amount of data in a production environment. The next step is to take our Docker images, described by CWL/WDL and run them in an environment that supports those descriptors. For now, we can suggest taking a look at the environments that currently support and are validated with CWL at [https://ci.commonwl.org/](https://ci.commonwl.org/) and for WDL, [Cromwell](https://github.com/broadinstitute/cromwell).

For developers, you may also wish to look at general commercial solutions such as [Google dsub](https://github.com/googlegenomics/task-submission-tools) and [AWS Batch](https://aws.amazon.com/batch/).

## Next Steps
We also recommend looking at the [Best Practices](/docs/publisher-tutorials/best-practices-toc/) before creating your first real tool/workflow.

## See Also
* [AWS Batch](/docs/publisher-tutorials/aws-batch/)
* [Azure Batch](/docs/publisher-tutorials/azure-batch/)
* [DNAstack Launch With](/docs/user-tutorials/dnastack-launch-with/)
* [FireCloud Launch With](/docs/user-tutorials/firecloud-launch-with/)