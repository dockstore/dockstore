# FireCloud Launch-With

Dockstore integrates with the FireCloud platform, allowing you to launch WDL-based workflows from Dockstore in FireCloud.  Here is some
information on what that looks like from a user point of view in a mini tutorial.

## Exporting into FireCloud

When browsing WDL workflows from within Dockstore, you will see a "Launch with FireCloud" button on the right. The currently selected
version of the workflow will be exported, but this can subsequently be changed in the FireCloud UI if need be.

![WDL workflow](/assets/images/docs/firecloud/firecloud_from_dockstore1.png)

If not logged into FireCloud, you will be prompted to login. Otherwise, or after login, you will be presented with the following screen. 

![WDL workflow import](/assets/images/docs/firecloud/firecloud_from_dockstore2.png)

You will need to pick a workspace to export it into. You can either select an existing workspace or create a new one.

Then hit the "Export" button and continue from within the FireCloud interface to configure and run your workflow.

If you decide to change the version of the Dockstore workflow within FireCloud, go to your workspace's Method Configurations tab.

![Method Configuration List](/assets/images/docs/firecloud/firecloud_workflows_2.png)

Click on the value in the name column, and then click "Edit Configuration".

![Method Configuration](/assets/images/docs/firecloud/firecloud_workflows_1.png)

Select the desired version in the dropdown.
 
Note that you will want to double-check that the workflow specifies a runtime environment (docker, cpu, memory,
and disks). 

## Limitations
0. While we support launching of WDL workflows, tools as listed in Dockstore are currently not supported.
0. FireCloud does not currently support file-path based imports.  Importing a workflow with file-based imports will result in error.  See the [converting file-based imports doc](/language-support/#converting-file-path-based-imports-to-public-https-based-imports-for-wdl) for more info.
0. Only the WDL language is supported.

## See Also

* [AWS Batch](/advanced-topics/aws-batch/)
* [Azure Batch](/advanced-topics/azure-batch/)
* [DNAnexus Launch With](dnanexus-launch-with/)
* [DNAstack Launch With](dnastack-launch-with/)
* [Terra Launch With](terra-launch-with/)
