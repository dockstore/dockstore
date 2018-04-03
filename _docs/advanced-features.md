---
title: Advanced Features
permalink: /docs/publisher-tutorials/advanced-features/
---
# Advanced Features

## File Provisioning

As a convenience, the dockstore command-line can perform file provisioning for inputs and outputs.

File provisioning for some protocols like HTTP and FTP is built in while other protocols are handled by plugins as documented [here](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent).

To illustrate, for this [tool](https://dockstore.org/containers/quay.io/collaboratory/dockstore-tool-bamstats) we provide a couple of parameter files that can be used to parameterize a run of bamstats.

In the following JSON file, this file indicates for a CWL run that the input file should be present and readable at `/tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam`. The output file will be copied to `/tmp/bamstats_report.zip` (which should be writeable).

```
{
  "bam_input": {
        "class": "File",
        "path": "/tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
    },
    "bamstats_report": {
        "class": "File",
        "path": "/tmp/bamstats_report.zip"
    }
}
```

The Dockstore command-line allows you to specify that the input file can be at a HTTP(S) location, an FTP location, an AWS S3 location, a [synapse id](http://python-docs.synapse.org/#accessing-data), or an [ICGC storage id](http://docs.icgc.org/cloud/guide/#cloud-guide) in place of that path. For example the following indicates that the input file will be downloaded under HTTP.

```
{
  "bam_input": {
        "class": "File",
        "path": "https://s3.amazonaws.com/oconnor-test-bucket/sample-data/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
    },
    "bamstats_report": {
        "class": "File",
        "path": "/tmp/bamstats_report.zip"
    }
}
```

Provisioning for output files works in the same way and has been tested with S3 output locations.

For some file provisioning methods, additional configuration may be required.

### AWS S3

For AWS S3, create a `~/.aws/credentials` file and a `~/.aws/config` file as documented at the following [location](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-config-files).

Get more information on the implementing plugin at [s3-plugin](https://github.com/dockstore/s3-plugin).

### ICGC Storage

For ICGC Storage, configure the location of the client using the configuration key `dockstore-file-icgc-storage-client-plugin.client` in `~/.dockstore/config` under the section `[dockstore-file-icgc-storage-client-plugin]`. Then configure the ICGC storage client as documented [here](http://docs.icgc.org/cloud/guide/#configuration).

Get more information on the implementing plugin at [icgc-storage-client-plugin](https://github.com/dockstore/icgc-storage-client-plugin).


### Synapse

For Synapse, you can add `synapse-api-key` and `synapse-user-name` to `~/.dockstore/config` under the section `[dockstore-file-synapse-plugin]`.

Get more information on the implementing plugin at [synapse-plugin
](https://github.com/dockstore/synapse-plugin).


## Input File Cache

When developing or debugging tools, it can be time consuming (and space-consuming) to repeatedly download input files for your tools. A feature of the Dockstore CLI is the ability to cache input files locally so that they can be quickly re-used for multiple attempts at launching a tool.

This feature relies upon Linux [hard-linking](https://en.wikipedia.org/wiki/Hard_link) so when enabling this feature, it is important to ensure that the location of the cache directory (by default, at `~/.dockstore/cache/`) is on the same filesystem as the working directory where you intend on running your tools.

There are two configuration file keys that can be used to activate input file caching and to configure the location of the cache.  These are added (or changed) inside your configuration file at `~/.dockstore/config`.

```
use-cache = true
cache-dir =
```

The former is false by default and can be set to true in order to activate the cache.
The latter is `~/.dockstore/cache/` by default and can be set to any directory location.

## File Provision Retries

By default, Dockstore will attempt to download files up to three times. Control this with the `file-provision-retries` parameter inside `~/.dockstore/config`.

## Running CWL-runner with extra tags

When running a CWL tool, you may want to add additional parameters/flags to the cwl-runner command. You can do this by updating your dockstore config file (`~/.dockstore/config`).

As an example, adding the following line to your config file will stop cwl-runner from cleaning up (the Docker container and the temp directory as mounted on the host) and make it run in debug mode.

```
cwltool-extra-parameters: --debug, --leave-container, --leave-tmpdir
```

## Alternative CWL Launchers:

By default, the dockstore CLI launches tools/workflows using [cwltool](https://github.com/common-workflow-language/cwltool). However, we have an experimental integration with several other launchers such as:
- [Rabix Bunny](https://github.com/rabix/bunny) v1.0.2
- [cwl-runner](http://www.commonwl.org/v1.0/CommandLineTool.html#Executing_CWL_documents_as_scripts)

For bunny, activate it by adding the following to your `~/.dockstore/config`:
```
cwlrunner: bunny
```
This will download rabix into `~/.dockstore/libraries/` when launching tools/workflows. This has not been thoroughly tested, but we have had some success running tools and workflows using this alternative.
Additionally, you can override the bunny version in the config file using:
```
bunny-version = 1.0.4-4
```

If your workflow platform provides the cwl-runner alias as the platform's default CWL implementation, you can activate it by adding the following to your `~/.dockstore/config`:
```
cwlrunner: cwl-runner
```

Furthermore, even though it's the default, you can also explicitly use cwltool by adding the following to your `~/.dockstore/config`:
 ```
 cwlrunner: cwltool
 ```

Keep in mind that there are a few differences in how locked-down the Docker execution environments are between any two alternatives, so a workflow that succeeds in one may not necessarily succeed in the other.

You can test all the launchers by cloning the dockstore-tool-md5sum repository: `git clone git@github.com:briandoconnor/dockstore-tool-md5sum.git`
Then test with cwl-runner, cwltool, and bunny using `dockstore tool launch --local-entry Dockstore.cwl --json test.json` after the above configurations have been made.

## WDL Launcher Configuration

By default, WDL tools/workflows will automatically be ran with [cromwell](https://github.com/broadinstitute/cromwell) 29.
Additionally, you can override the cromwell version in the config file using:
```
cromwell-version = 30.2
```

You can test cromwell by git cloning git@github.com:briandoconnor/dockstore-tool-md5sum.git and using `dockstore tool launch --local-entry Dockstore.wdl --json test.wdl.json`

## TRS With Checker Workflows
You can interact with checker workflows using the TRS in the same way you interact with regular workflows.

The Swagger UI for the the TRS endpoints can be found [here](https://dockstore.org:8443/static/swagger-ui/index.html#/GA4GH).

For this section we will reference the tool and checker workflow mentioned in the [Checker workflows](/docs/publisher-tutorials/checker-workflows/) tutorial.

### Distinguishing Between an Entry and a Checker Workflow
We can use the TRS to find all entries on Dockstore, but how do we know which are checker workflows?

Run the following command to retrieve all entries in Dockstore (limit to 1000):
```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools?limit=1000" -H "accept: application/json"
```

There are two fields to look for in the results. The first is the `has_checker` field, which will tell you wether or not the entry has a checker workflow. If it does have a checker workflow, then the `checker_url` field contains a link to the checker workflow using the TRS. This is how we can determine if an entry has a checker, and also interact with the associated checker.

Once we have the checker URL, we can start to interact with it like a regular workflow.

### Retrieve the checker workflow
Lets use the TRS to retrieve the checker workflow from the checker workflows tutorial mentioned above. The following command will retrieve the checker workflow object. Note that this is simply using the typical TRS endpoint
to grab an entry.

```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker" -H "accept: application/json"
```

### Retrieve descriptors
If we want the primary descriptor for our checker workflow, we can use the following command:

```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/descriptor" -H "accept: application/json"
```

Note that this command requires a version and descriptor type.

If we want to grab the secondary descriptor files, we must first get a list of secondary files:

```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/files" -H "accept: application/json"
```

This will return an array of files, including the primary descriptor, secondary descriptors, and test files.

We can grab a descriptor file using the following command:

```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/descriptor/checker%2Fmd5sum-checker.cwl" -H "accept: application/json"
```

### Retrieve the Test Parameter Files
We can retrieve the test parameter files for a checker workflow using the following command:
```
curl -X GET "https://dockstore.org:8443/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/tests" -H "accept: application/json"
```

Again, we must specify the version and descriptor type in the command.

This will return an array of objects representing the test parameter files. Each object contains two fields, test and url.
- **test**: the test file
- **url**: the relative path

### Other Usage
This does not cover all of the uses of the TRS for checker workflows. You can see the full list of commands  [here](https://dockstore.org:8443/static/swagger-ui/index.html#/GA4GH). Note that any call that works for a normal tool or workflow will also work with a checker workflow.
