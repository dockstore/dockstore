---
title: Publishing Zip Files
permalink: /docs/publisher-tutorials/publishing-zips/
---

> For Dockstore 1.6.0+

# Intro

Dockstore 1.6 has introduced a <a href="https://dockstore.org/api/static/swagger-ui/index.html#/hosted/addZip">new API</a>
to publish the contents of a ZIP file as a new hosted workflow version. This API makes it easier to programatically
assemble and publish workflows.

This API works in conjunction with the 
<a href="/docs/publisher-tutorials/hosted-tools-and-workflows/">Hosted Tools and Workflows</a>
Dockstore feature. The contents of the zip are stored directly on Dockstore.

## Use Cases

1. As workflows become more complex, it may not be possible to have a simple mapping
between a Dockstore workflow and a source code repository. Source code repos
might make use of submodules; workflows that users are expected to run might get assembled
from several modules.
2. Workflows should be tested before being published. A Continuous Integration server such
as Travis CI or Circle CI could run tests when a new commit to a workflow is pushed,
and if the tests pass, a new version of the workflow can be published.
3. Workflow developers might want to have continuous deployment of their workflows.

## The ZIP File Contents

The ZIP file should contain:

* The workflow's source files -- the actual source files such .wdl and .cwl. At least one file is required.
* Other file types used by the workflows, e.g., CWL can include JavaScript, Python, etc.
files. This is optional.
* Test parameter files. Optional JSON test parameter files.
* A .dockstore.yml file in the root of the zip file. This is a manifest describing the 
contents of the zip file and is required. The structure of this file is described in
the next section.

### The .dockstore.yml file

The .dockstore.yml is a manifest that describes the contents of the zip. It must be
present at the root of the zip. The syntax of the file is described in the
following example.

#### Example

```
# The dockstore.yml version. Required and value must be `1.0`.
dockstoreVersion: 1.0
# The class this manifest is defining. Currenly only `workflow` is supported. Required.
class: workflow
# Path to the primary descriptor with the archive. Required and the file must exists.
primaryDescriptor: main/hello.wdl
# Optional list of test parameter files
testParameterFiles:
  - test.wdl.json
  - test2.wdl.json
# An optional map with metadata key-value pairs you can you use to describe your workflow.
# The key names and values can be anything you like.
# Currently Dockstore does not do anything with this information, but it does save
# the .dockstore.yml, this will change in the future.
metadata:
  gitSha: 123456
  gitRelease: 1.3.0
  publisher: Dockstore
```

### Limits

There are limits on:

* The number of hosted workflows you can create.
* The number of versions each hosted workflow can contain.
* The size of the compressed zip
* The size of the uncompressed zip.

Because these limits may vary over time and per user, they are not listed here, but if you run into any of these
limits, please contact the Dockstore team.

## Tutorial

Following is a simple example using curl to create a hosted workflow, creating a zip, and posting the zip
to create a version.

You will need to have registered with Dockstore, and you need your Dockstore token. To get
your Dockstore token, after you have logged in, go to your 
<a href="https://dockstore.org/accounts">Accounts</a> page, and copy the token to your
clipboard by clicking on the Copy icon next to the token in your Dockstore Account section.

In a terminal window, assign the token to a environment variable:

```
set DOCKSTORE_TOKEN="<value in clipboard>"
```

Create a hosted workflow. This will create the workflow without any versions. We will give it the name `tutorial`,
and let's make it a WDL workflow.

```
curl -X POST "https://dockstore.org/api/workflows/hostedEntry?name=tutorial&descriptorType=wdl" -H "accept: application/json" -H "Authorization: Bearer ${DOCKSTORE_TOKEN}"
```

The response to the above will be a JSON that will include a property named `id`. Save the value of the id, `8648`
in this example.

```
{
  "aliases": {},
  "author": null,
  "checker_id": null,
  "dbCreateDate": 1552432560625,
  "dbUpdateDate": 1552432560625,
  "defaultTestParameterFilePath": "/test.json",
  "defaultVersion": null,
  "description": null,
  "descriptorType": "wdl",
  "email": null,
  "full_workflow_path": "dockstore.org/tutorialuser/tutorial",
  "gitUrl": "git@dockstore.org:workflows/dockstore.org/tutorialuser/tutorial.git",
  "has_checker": false,
  "id": 8648,
  ...
```

Let's save it as an environment variable:

```
set WORKFLOW_ID=8648
```

Now that you have the hosted workflow, create the ZIP file that will contain the first version of the workflow.

Create and navigate to an empty directory. Create a myWorkflow.wdl file with these contents, which come from
<a href="https://cromwell.readthedocs.io/en/develop/tutorials/FiveMinuteIntro/">Cromwell.readthedocs.io</a>.

```
workflow myWorkflow {
    call myTask
}

task myTask {
    command {
        echo "hello world"
    }
    output {
        String out = read_string(stdout())
    }
}
```
Create a .dockstore.yml with the following content:

```
dockstoreVersion: 1.0
class: workflow
primaryDescriptor: myWorkflow.wdl
```

Combine the two files into a .zip:

```
zip firstversion.zip myworkflow.wdl .dockstore.yml
```

Create a new version using the zip:

```
curl -X POST "https://dockstore.org/api/workflows/hostedEntry/${WORKFLOW_ID}" -H "accept: application/json" -H "Authorization: Bearer ${DOCKSTORE_TOKEN}" -H "Content-Type: multipart/form-data" -F "file=@firstversion.zip;type=application/zip"
```
