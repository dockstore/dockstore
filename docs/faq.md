# FAQ

## How does launching with Dockstore CLI compare with cwltool?

The Dockstore CLI has utilities to generate JSON parameter files from entries on Dockstore (`dockstore tool convert`).

When launching tools, the Dockstore CLI makes it easy to specify entries from Dockstore.
We can also provision input and output files using HTTP, FTP, and S3. We also have preliminary support for [Synapse](https://www.synapse.org/) and the [ICGC Storage client](https://docs.icgc.org/download/guide/#score-client-usage). Please see [file provisioning plugins](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent) for more information on these two file transfer sources.

## What environment do you test tools in?

Typically, we test running tools in Ubuntu Linux 16.04 LTS on VMs in [OpenStack](https://www.openstack.org/) with 8 vCPUs and 96 GB of RAM and above. We've also begun testing on Ubuntu 18.04 LTS and so far it's been successful. If you are only listing and editing tools, we have achieved success with much lower system requirements. However, launching tools will have higher system requirements dependent on the specific tool. Consult a tool's README or CWL/WDL description when in doubt.

## There are too many versions of my tool, how do I delete some?

Versions of your tool for most tools are harvested from the list of Tags for an image on Quay.io, [as an example](https://quay.io/repository/pancancer/pcawg-bwa-mem-workflow?tab=tags). If you have the right permissions, you can delete some and then refresh a tool on Dockstore to clean-up.

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
...
```


Also be aware that some tools will use space from your root filesystem. For example, Docker's storage driver and data volumes will by default install to and use space on your root filesystem.

## Do you have tips on creating Dockerfiles?

* make sure you [manage Docker as a non-root user](https://docs.docker.com/install/linux/linux-postinstall/) on your system so you do not need sudo for the `docker` command
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
    * do not "package" large data reference files in your Docker image.  Instead, treat them as "inputs" so they can be staged outside and mounted into the running container
    * the `$TMPDIR` variable can be used as a scratch space inside your container.  Make sure your host running Docker has sufficient scratch space for processing your genomics data.

## How do I use the Dockstore CLI on a Mac?
See [Docker for Mac](https://docs.docker.com/engine/installation/mac/)

.. note:: 
    Docker behaves a bit differently on a [Mac](https://docs.docker.com/docker-for-mac/osxfs/#/namespaces) than on a typical Ubuntu machine. By default the only shared volumes are /Users, /Volumes, /tmp, and /private. Note that /var is not a shared directory (and can't be set as one). `cwltool` uses your TMPDIR (the env variable) to setup volumes with docker, which on a Mac can default to a subdirectory of /var. In order to get `cwltool` working on your Mac, you need to set your TMPDIR to be under one of the shared volumes in Docker for Mac. You can do this by doing something similar to the following:

```
export TMPDIR=/tmp/docker_tmp
```
By default, Docker for Mac allocates fewer resources (CPU, Memory, Swap) to containers compared to what is available on your host machine. You can change what it allocates using the Docker for Mac GUI under `Preferences > Advanced` as described [here](https://docs.docker.com/docker-for-mac/#advanced).
* The default allocation can cause workflows or tools to fail without informing the user with a memory or resource related error message. If you find that your workflow or tool is behaving differently on a Mac compared to a similarly resourced Ubuntu environment, you can try increasing the resources allocated to Docker on the Mac to resolve the discrepancy.

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

This is going to be a moving quality target as tools improve, but if you have a tool that you wish to be verified, please send us a heads-up via our GitHub issues or Gitter!

## What is a default version of a tool or workflow?

Every tool/workflow is recommended to have a default version set by its author. It indicates to the end users which version of the tool/workflow they should use.
For tools, the default version is uniquely identified by the tag from the Docker image repository.  For workflows, the default version is identified by the Git Reference (which could be a Git tag or a Git branch).  The default version can be set in the 'Versions' tab of a tool/workflow via radio buttons.

Setting the default version affects a number of elements including (but not limited to):

1. It determines what is displayed in the 'Description' section of the 'Info' Tab
1. It is the first version other end users see when no version is specified. For example https://dockstore.org/containers/quay.io/pancancer/pcawg-bwa-mem-workflow is redirected to https://dockstore.org/containers/quay.io/pancancer/pcawg-bwa-mem-workflow:develop?tab=info 
1. It is the version of the tool/workflow that is launched by default when users launch a tool/workflow from the Dockstore CLI.  
   For example, if version 1.0 is set as the default version of the quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig tool,

   `$ dockstore tool launch --entry quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig:1.0 --json Dockstore.json`

   would be equivalent to

   `$ dockstore tool launch --entry quay.io/cancercollaboratory/dockstore-tool-bedgraph-bigwig --json Dockstore.json` 
1. The docker pull command in the tools search reflects the default version


## How can I use the Dockstore CLI with Python 3?

There are currently issues with avro, cwltool, and Python 3.  See [cwltool](https://github.com/common-workflow-language/cwltool/issues/524) for more info.  To work around this issue, instead of installing avro, install avro-cwl.  Therefore, the pip3 requirements.txt file should end up looking like [this](https://dockstore.org/api/metadata/runner_dependencies?client_version=1.5.0&python_version=3&runner=cwltool&output=text)

Note that installing the "avro" pip package afterwards will overwrite the "avro-cwl" pip package and will result in cwltool not working again.

## How do I add other users as maintainers of a tool?
For tools registered on Quay.io and workflows registered with GitHub, Dockstore automatically allows users from the same Quay.io organization or GitHub organization to manage tools/workflows together (users will need to "Refresh Organization" or "Refresh All"). 

For tools registered on Docker Hub, GitLab or private registries, this feature currently does not exist because these registries do not allow the retrieval of organization information.
Likewise, workflows registered with other source code repositories lack this feature.

Finally, for participants of the [limited sharing beta](../advanced-topics/sharing-workflows/), you can enter the email addresses of the users you wish to share with to give them permissions to your workflow. This is only available for hosted workflows and users with Google accounts linked to FireCloud.

## Why are my workflows from an organization I belong to not visible?
Organizations have the ability to restrict access to the API for third party applications. GitHub provides a [tutorial](https://help.github.com/articles/enabling-oauth-app-access-restrictions-for-your-organization/) on how to add these restrictions to your organizations.

In order for Dockstore to gain access to organizations of this type, you will need to grant access to the Dockstore application. Dockstore will only be reading information on workflows in your organization and who has access to them in order to mirror these restrictions on Dockstore itself. GitHub provides a [tutorial](https://help.github.com/articles/approving-oauth-apps-for-your-organization/) for approving third party apps access to your organization.

## What is the difference between logging in with GitHub or logging in with Google?
The intent here is that you should be able to login with either login method and still conveniently get into the same Dockstore account. With login via Google, if you are a FireCloud user you will also have access to [sharing functionality](../advanced-topics/sharing-workflows/).

Note that for simplicity, each of your GitHub or Google accounts can only be associated with one account at a time. You will need to link with a different account for each login method or delete your account if you want to assign them to a new Dockstore account. 

## How do I launch tools/workflows without internet access on compute nodes
> For Dockstore 1.6.0+

Some tools/workflows require Docker images to launch even if they are local entries.  If the compute nodes do not have internet access, you can follow these steps:
1. download the Docker image(s) on the head node which does have internet access using the `docker save -o <filename> <imagename>`
1. ensure that the `<imagename>` matches the image name specific in the CWL or WDL descriptor
1. place the image file(s) in a location that the compute nodes have access to (make sure there are only images in that directory)
1. specify in the dockstore config file (default ~/.dockstore/config) the directory that contains your image(s) using `docker-images = /home/user/docker_images_directory`

The Dockstore CLI will automatically load all Docker images in the directory specified prior to a `launch --local-entry` command

## Any last tips on using Dockstore?

* the Dockstore CLI uses `./datastore` in the working directory for temp files so if you're processing large files make sure this partition hosting the current directory is large.
* you can use a single Docker image with multiple tools, each of them registered via a different CWL
* you can use a Git repository with multiple CWL files
* related to the two above, you can use non-standard file paths if you customize your registrations in the Version tab of Dockstore