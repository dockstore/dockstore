---
title: Publishing Zip Files
permalink: /docs/publisher-tutorials/publishing-zips/
---

> For Dockstore 1.6.0+

# Intro

Dockstore 1.6 has introduced a new API to publish the contents of a ZIP file
as a new hosted workflow version. This API makes it easier to programatically
assemble and publish workflows.

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

* The workflow's source files all of the workflow's files -- the actual source files such
.wdl and .cwl. At least one file is required.
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

Following is a simple example using curl to create a hosted workflow and publish

### Requirements

* An account on Dockstore
