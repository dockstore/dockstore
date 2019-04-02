---
title: Terra Launch-With
permalink: /docs/user-tutorials/terra-launch-with/
---
# Intro

Dockstore integrates with the Terra platform, allowing you to launch WDL-based workflows from Dockstore in Terra.  Here is some
information on what that looks like from a user point of view in a mini tutorial.

## Exporting into Terra

When browsing WDL workflows from within Dockstore, you will see a "Launch with Terra" button on the right. The currently selected
version of the workflow will be exported.

![WDL workflow](/assets/images/docs/terra/terra_from_dockstore1.png)

If not logged into Terra, you will be prompted to login. Otherwise, or after login, you will be presented with the following screen. 

![WDL workflow import](/assets/images/docs/terra/terra_from_dockstore2.png)

You will need to pick a workspace to export it into. You can either select an existing workspace or create a new one.

Then hit the "Import" button and continue from within the Terra interface to configure and run your workflow.

Note that you will want to double-check that the workflow specifies a runtime environment (docker, cpu, memory,
and disks). 

## Limitations
1. While we support launching of WDL workflows, tools as listed in Dockstore are currently not supported.
1. Terra does not currently support file-path based imports.  Importing a workflow with file-based imports will result in error.  See [developer docs](/docs/publisher-tutorials/for-developers/#converting-file-path-based-imports-to-public-https-based-imports) for more info.
1. Only the WDL language is supported.

## See Also

* [AWS Batch](/docs/publisher-tutorials/aws-batch/)
* [Azure Batch](/docs/publisher-tutorials/azure-batch/)
* [DNAnexus Launch With](/docs/user-tutorials/dnanexus-launch-with/)
* [DNAstack Launch With](/docs/user-tutorials/dnastack-launch-with/)
* [FireCloud Launch With](/docs/user-tutorials/firecloud-launch-with/)
