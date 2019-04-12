# Batch Services

Dockstore tools and workflows can also be run through a number of online services that we're going to loosely call "commercial batch services." These services share the following characteristics: they spin up the underlying infrastructure and run commands, often in Docker containers, while freeing you from running the batch computing software yourself. While not having any understanding of CWL, these services can be used naively to run tools and workflows, and in a more sophisticated way to implement a CWL-compatible workflow engine.  

### AWS Batch

[AWS Batch](https://aws.amazon.com/batch/) is built by Amazon Web Services. Look [here](aws-batch) for a tutorial on how to run a few sample tools via AWS.

### Azure Batch

[Azure Batch](https://azure.microsoft.com/en-us/services/batch/) and the associated [batch-shipyard](https://github.com/Azure/batch-shipyard) is built by Microsoft. Look [here](azure-batch) for a tutorial on how to run a few sample tools via Azure.

### Google Pipelines

Google Pipeline and [Google dsub](https://github.com/googlegenomics/dsub) are also worth a look. In particular, both [Google Genomics Pipelines](https://cloud.google.com/genomics/v1alpha2/pipelines) and [dsub](https://cloud.google.com/genomics/v1alpha2/dsub) provide tutorials on how to run  (Dockstore!) tools if you have some knowledge on how to construct the command-line for a tool yourself.

## Consonance

Consonance pre-dates Dockstore and was the framework used to run much of the data analysis for the [PCAWG](https://dcc.icgc.org/pcawg#!%2Fmutations) project by running [Seqware](https://seqware.github.io/) workflows. Documentation for this incarnation of Dockstore can be found at [Working with PanCancer Data on AWS](http://icgc.org/working-pancancer-data-aws) and [ICGC on AWS](https://aws.amazon.com/public-datasets/icgc/).

Consonance has subsequently been updated to run Dockstore tools and has also been adopted at the [UCSC Genomics Institute](https://github.com/BD2KGenomics/dcc-ops) for this purpose. Also, using cwltool under-the-hood to provide CWL compatibility, Consonance provides DIY open-source support for provisioning AWS VMs and starting CWL tasks. We recommend having some knowledge of AWS EC2 before attempting this route.

Consonance's strategy is to provision either on-demand VMs or spot priced VMs depending on cost, and delegates runs of CWL tools to these provisioned VMs with one tool executing per VM. A Java-based web service and RabbitMQ provide for communication between workers and the launcher, while an Ansible playbook is used to setup workers for execution.

### Usage

1. Look at the [Consonance](https://github.com/Consonance/consonance) repo and, in particular, the [Docker compose based](https://github.com/Consonance/consonance/tree/develop/container-admin) setup instructions to setup the environment
2. Once logged into the client
```
consonance run --tool-dockstore-id quay.io/collaboratory/dockstore-tool-bamstats:1.25-6_1.0 --run-descriptor Dockstore.json --flavour <AWS instance-type>
```