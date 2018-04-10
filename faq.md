---
layout: faq
title: FAQ
permalink: /faq/
---

# FAQ (Frequently Asked Questions)

## How does launching with Dockstore CLI compare with cwltool?

The Dockstore CLI has utilities to generate JSON parameter files from entries on Dockstore (`dockstore tool convert`).

When launching tools, the Dockstore CLI makes it easy to specify entries from Dockstore.
We can also provision input and output files from and to HTTP, ftp, and S3. We also have preliminary support for [Synapse](https://www.synapse.org/) and the [ICGC Storage client](http://docs.icgc.org/cloud/guide/#storage-client-usage), please see [file provisioning plugins](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent) get more information on these two file transfer sources.

## What environment do you test tools in?

Typically, we test running tools in Ubuntu Linux 14.04 LTS and 16.04 LTS on VMs in [OpenStack](https://www.openstack.org/) with 8 vCPUs and 96 GB of RAM and above. If you are only listing and editing tools, we have achieved success with much lower system requirements. However, launching tools will have higher system requirements dependent on the specific tool. Consult a tool's README or CWL description when in doubt.

## There are too many versions of my tool, how do I delete some?

Versions of your tool for most tools are harvested from the list of Tags for an image on quay.io, [as an example](https://quay.io/repository/pancancer/pcawg-bwa-mem-workflow?tab=tags). If you have the right permissions, you can delete some and then refresh a tool on Dockstore to clean-up.

## How do I cite Dockstore?

For citing Dockstore as a paper, take a look at our [F1000 paper](http://dx.doi.org/10.12688/f1000research.10137.1).

For citing the actual code, we recommend looking at our Zenodo entry. You will find a variety of citation styles and ways to export it at [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.321679.svg)](https://doi.org/10.5281/zenodo.321679).

## How do I get more space inside my CWL tool running in a container?

There are a couple different answers here. Different directories inside a container run by CWL are mounted from different locations and will impose different storage requirements.

Outside the container, the Dockstore CLI will create a directory called `datastore` which contains input files provisioned for the running container. For CWL tools, this directory will include the working directory (`datastore/<uuid>/working`), the temporary directory (`datastore/<uuid>/tmp/<random>`), and input files (`datastore/<uuid>/inputs`).

For the exact breakdown, keep an eye on the invocation of cwltool when launching a tool. For example, the following means that the containers working directory is stored at `/media/dyuen/Data/large_volume/datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/working/ZQn2hv` on the underlying host and the temporary directory at `/media/dyuen/Data/large_volume/datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/tmp/H1xA_N`:
```
$ dockstore tool launch  --local-entry Dockstore.cwl --json sample_configs.local.actual.json
Creating directories for run of Dockstore launcher at: ./datastore//launcher-22838dbe-044e-4ee5-8532-4cf405222439
Provisioning your input files to your local machine
Downloading: #bam_input from rna.SRR948778.bam into directory: /media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/inputs/8beb90df-f193-493c-b834-ed28973015e3
Calling out to cwltool to run your tool
Executing: cwltool --enable-dev --non-strict --outdir /media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/outputs/ --tmpdir-prefix /media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/tmp/ --tmp-outdir-prefix /media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/working/ /media/dyuen/Data/large_volume/Dockstore.cwl /media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/workflow_params.json
/usr/local/bin/cwltool 1.0.20170217172322
Resolved '/media/dyuen/Data/large_volume/Dockstore.cwl' to 'file:///media/dyuen/Data/large_volume/Dockstore.cwl'
[job Dockstore.cwl] /media/dyuen/Data/large_volume/datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/working/ZQn2hv$ docker \
    run \
    -i \
    --volume=/media/dyuen/Data/large_volume/./datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/inputs/8beb90df-f193-493c-b834-ed28973015e3/rna.SRR948778.bam:/var/lib/cwl/stgc2b37b55-005a-43e5-a824-6caf6656d9c2/rna.SRR948778.bam:ro \
    --volume=/media/dyuen/Data/large_volume/datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/working/ZQn2hv:/var/spool/cwl:rw \
    --volume=/media/dyuen/Data/large_volume/datastore/launcher-22838dbe-044e-4ee5-8532-4cf405222439/tmp/H1xA_N:/tmp:rw \
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
    /var/lib/cwl/stgc2b37b55-005a-43e5-a824-6caf6656d9c2/rna.SRR948778.bam
<the rest of the output is snipped>
```


Also be aware that some tools will use space from your root filesystem. For example, Docker's storage driver and data volumes will by default install to and use space on your root filesystem.

## Do you have tips on creating Dockerfiles?

* make sure you [set up Docker command](https://docs.docker.com/engine/installation/linux/ubuntulinux/#/create-a-docker-group) on your system so you do not need sudo
* do not call Docker-inside-Docker (it's possible but causes Docker client/server issues, it is also not compatible with CWL)
* do not depend on changes to `hostname` or `/etc/hosts`, Docker will interfere with this
* try to keep your Docker images small


## Do you have tips on creating CWL files?

When writing CWL tools and workflows, there are a few common workarounds that can be used to deal with the restrictions that CWL places on the use of docker. These include:
* cwltool (which we use to run tools) is restrictive and locks down much of `/` as read only, use the current working directory or $TMPDIR for file writes
  * You can also use [Docker volumes](https://docs.docker.com/engine/reference/builder/#/volume) in your Dockerfile to specify additional writeable directories
* Do not rely on the hostname inside a container, Docker dynamically generates this when starting containers

Additionally:

* you need to "collect" output from your tools/workflows inside docker and drop them into the current working directory in order for CWL to "find" them and pull them back outside of the container
* related to this, it's often times easiest to write a simple wrapper script that maps the command line arguments specified by CWL to however your tool expects to be parameterized. This script can handle moving output to the current working directory and renaming if need be
* genomics workflows work with large data files, this can have a few ramifications:
    * do not "package" large data reference files in your Docker image.  Instead, treat them as "inputs" so they can be stagged outside and mounted into the running container
    * the `$TMPDIR` variable can be used as a scratch space inside your container.  Make sure your host running Docker has sufficient scratch space for processing your genomics data.

## How do I use the Dockstore CLI on a Mac?
Use [Docker for Mac](https://docs.docker.com/engine/installation/mac/)

Note:
Docker behaves a bit differently on a [mac](https://docs.docker.com/docker-for-mac/osxfs/#/namespaces) than on a typical ubuntu machine. By default the only shared volumes are /Users, /Volumes, /tmp, and /private. Note that /var is not a shared directory (and can't be set as one). CWLtool uses your TMPDIR (the env variable) to setup volumes with docker, which on a Mac can default to a subdirectory of /var. In order to get CWLtool working on your mac, you need to set your TMPDIR to be under one of the shared volumes in Docker for Mac. You can do this by doing something similar to the following:

```
export TMPDIR=/tmp/docker_tmp
```

## What is a verified tool or workflow?

For certain tools or workflows on Dockstore, you will see a verified checkmark like <a href="/faq#what-is-a-verified-tool-or-workflow"><span class="glyphicon glyphicon-ok"></span></a>. This checkmark is used to indicate tools that were either:

* created by our users and verified by our team to run successfully.
* created by our team and verified by an outside party to run successfully

Currently, the majority of tool validation has been done by the [docktesters](https://lists.icgc.org/mailman/listinfo/docktesters) team currently headed by Miguel Vazquez and formerly headed by Francis Ouellette.

We also strive to use this to highlight tools that share a common set of recommended characteristics:

* tools should include a description and an author
* tools should include a README.md or similar in their source repo describing any other relevant information about the tool
* tools should include at least one test parameter file indicating how to run the tool on some sample data
* the Dockerfile should be helpful in reconstructing how a tool was built from source
* tools and/or their reference data should be publically available

This is going to be a moving quality target as tools improve, but you have a tool that you wish to be verified, please send us a heads-up via our GitHub issues or Gitter!

## What is a default version of a tool or workflow?

Every tool/workflow is recommended to have a default version set by its author.  It indicates to the users which version of the tool/workflow they should use.
For tools, the default version is uniquely identified by the tag from the Docker image repository.  For workflows, the default version is identified by the Git Reference (which could be a Git tag or a Git branch).  The default version can be set in the 'Versions' tab of a tool/workflow via radio buttons.

Setting the default version affects a number of elements including (but not limited to):
1. It determines what is displayed in the 'Description' section of the 'Info' Tab
2. It determines what is displayed in the 'Launch With' section of the 'Info' Tab
3. It is the version of the tool/workflow that is launched by default when users launch a tool/workflow from the Dockstore CLI.  
   For example, if version 1.0 is set as the default version of the 'quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig' tool,<br />  
   `$ dockstore tool launch --entry quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig:1.0 --json Dockstore.json`<br />
   would be equivalent to  <br />
   `$ dockstore tool launch --entry quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig --json Dockstore.json`  

4. In the files tab, the default version's files would be displayed first.
5. The docker pull command reflects the default version

## How to run Dockstore with Python 3?

There are currently issues with avro, cwltool, and Python 3.  See [cwltool](https://github.com/common-workflow-language/cwltool/issues/524) for more info.  To work around this issue, instead of installing avro==1.8.1, install avro-cwl==1.8.3.  Therefore, the pip3 requirements.txt file should end up looking like:
```
setuptools==36.5.0
cwl-runner
cwltool==1.0.20170828135420
schema-salad==2.6.20170806163416
avro-cwl==1.8.3
ruamel.yaml==0.14.12
requests==2.18.4
```

Note that installing the "avro" pip package afterwards will overwrite the "avro-cwl" pip package and will result in cwltool not working again.

## Any last tips on using Dockstore?

* the Dockstore CLI uses `./datastore` in the working directory for temp files so if you're processing large files make sure this partition hosting the current directory is large.
* you can use a single Docker image with multiple tools, each of them registered via a different CWL
* you can use a Git repository with multiple CWL files
* related to the two above, you can use non-standard file paths if you customize your registrations in the Version tab of Dockstore
