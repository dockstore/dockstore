---
title: DNAnexus Launch-With
permalink: /docs/user-tutorials/dnanexus-launch-with/
---
# Intro

Dockstore integrates with the DNAnexus platform, allowing you to launch WDL-based workflows from Dockstore in DNAnexus.  Here is some
information on what that looks like from a user point of view in a mini tutorial.

## Exporting into DNAnexus

When browsing WDL workflows from within Dockstore, you will see a "Launch with DNAnexus" button on the right. The currently selected
version of the workflow will be exported.

![WDL workflow](/assets/images/docs/dnanexus/dnanexus_from_dockstore1.png)

If not logged into DNAnexus, you will be prompted to login. Otherwise, or after login, you will be presented with the following screen. 

![WDL workflow import](/assets/images/docs/dnanexus/dnanexus_from_dockstore2.png)

You will need to pick a folder to export it into. You can either select a folder from an existing project, or you can create a new project.

Then hit the "Submit" button and continue from within the DNAnexus interface to configure and run your workflow.

## Limitations
1. While we support launching of WDL workflows, tools as listed in Dockstore are currently not supported.
1. Only the WDL language is supported.

## See Also

* [AWS Batch](/docs/publisher-tutorials/aws-batch/)
* [Azure Batch](/docs/publisher-tutorials/azure-batch/)
* [DNAstack Launch With](/docs/user-tutorials/dnastack-launch-with/)
* [FireCloud Launch With](/docs/user-tutorials/firecloud-launch-with/)
* [Terra Launch With](/docs/user-tutorials/terra-launch-with/)
