# Posting Zip Files

Dockstore has an [API](https://dockstore.org/api/static/swagger-ui/index.html#/hosted/addZip)
to post the contents of a ZIP file as a new hosted workflow version. This API makes it easier to
programatically post workflow versions that have been tested and produced by a build process.

This API works in conjunction with the 
[Hosted Tools and Workflows](../getting-started/hosted-tools-and-workflows/)
Dockstore feature. The contents of the zip are stored directly on Dockstore.

## Use Cases

1. As workflows become more complex, it may not always be possible to have a simple mapping
between a Dockstore workflow and a source code repository. Source code repos
might make use of Git submodules; workflows that users are expected to run might get assembled
from several repos.
2. Workflows should be tested before being published. A Continuous Integration server such
as Travis CI or Circle CI could run tests when a new commit to a workflow is pushed,
and if the tests pass, a new version of the workflow can be pushed to Dockstore.

## The ZIP File Contents

The ZIP file that is posted to Dockstore should contain:

* The workflow descriptors; the CWL, WDL, etc.. At least one descriptor is required.
* Other file types used by the workflows, e.g., CWL, can include JavaScript, Python, etc.
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
# The dockstore.yml version. Required; value must be "1.0".
dockstoreVersion: 1.0

# The class this manifest is defining. Currenly only "workflow" is supported. Required.
class: workflow

# Path to the primary descriptor within the archive. The primary descriptor is
# the file that will first get executed when the workflow is launched.
# Required and the file must exist.
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

File names and paths in the .dockstore.yml are relative to the root of the zip. For
example, `primaryDescriptor: main.wdl` is referring to a `main.wdl` in the root of the
zip file. The path `main/hello.wdl` is referring to a `hello.wdl` file in the main 
directory off the root.

Files in the zip that are not listed in the manifest will still get added to the hosted
workflow.

## Limits

To prevent abuse, Dockstore has limits on the following:

* The number of hosted workflows a user can create.
* The number of versions each hosted workflow can contain.
* The size of the compressed zip
* The size of the uncompressed zip.

Because these limits may vary over time and per user, they are not listed here, but if you run into any of these
limits, please contact the Dockstore team.

## Tutorial

The following is a simple example using curl to create a hosted workflow, creating a zip, and posting the zip
to create a version.

You will need to have registered with Dockstore, and you need your Dockstore token. To get
your Dockstore token, after you have logged in, go to your 
[Accounts](https://dockstore.org/accounts) page, and copy the token to your
clipboard by clicking on the Copy icon next to the token in your Dockstore Account section.

In a terminal window, assign the token to an environment variable:

```
export DOCKSTORE_TOKEN="<value in clipboard>"
```

The following command creates a hosted WDL workflow, named `tutorial`, without any versions.

```
curl -X POST "https://dockstore.org/api/workflows/hostedEntry?name=tutorial&descriptorType=wdl" -H "accept: application/json" -H "Authorization: Bearer ${DOCKSTORE_TOKEN}"
```

The response to the above will be a JSON that will include a property named `id`.
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

Save the value of the id, `8648` in this example, to a variable.


```
export WORKFLOW_ID=8648
```

Now that you have the hosted workflow, create the ZIP file that will contain the first version of the workflow.

Create and navigate to an empty directory. Create a myWorkflow.wdl file with these contents (from 
[cromwell.readthedocs.io](https://cromwell.readthedocs.io/en/develop/tutorials/FiveMinuteIntro/)).

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
zip firstversion.zip myWorkflow.wdl .dockstore.yml
```

Create a new version of the hosted workflow using the zip:

```
curl -X POST "https://dockstore.org/api/workflows/hostedEntry/${WORKFLOW_ID}" -H "accept: application/json" -H "Authorization: Bearer ${DOCKSTORE_TOKEN}" -H "Content-Type: multipart/form-data" -F "file=@firstversion.zip;type=application/zip"
```

## Summary

This simple example should give you an idea of what you can do with this feature. With real-world
examples, you'll probably have multiple WDL or CWL files, with some of those files in
subdirectories. You won't want to post a version right away; you'll want to run tests first.

But once you have a workflow assembled and passing tests, all you need to do is assemble it
into a zip file and make an API call to get the contents on Dockstore.
