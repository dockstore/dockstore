---
title: Getting Started With WDL
permalink: /docs/prereqs/getting-started-with-wdl/
---
# Getting Started with WDL

This tutorial is a continuation of [Getting Started With Docker](/docs/prereqs/getting-started-with-docker/). Please complete that tutorial prior to doing this one.

## Describe Your Tool in WDL

You can describe tools via the [WDL language](https://github.com/openwdl/wdl).

In WDL, a tool can also be described as a one task WDL workflow.
We provide a hello world example as follows:

```
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
```

The runtime section of a task allows you to use a docker image to run the task in. This image should match the Dockerfile that you register on Dockstore alongside your WDL descriptor files.


## Next Steps

Follow the [next tutorial](/docs/publisher-tutorials/dockstore-account/) to create an account on Dockstore and link third party services.

## See Also
* [CWL](/docs/prereqs/getting-started-with-cwl/)
* [Nextflow](/docs/prereqs/getting-started-with-nextflow/)
* [Language Support](/docs/user-tutorials/language-support/)