# DNAstack Launch-With

Dockstore integrates with the DNAstack Workflows platform, allowing you to launch WDL-based workflows from Dockstore in DNAstack. This works both from within the Dockstore interface and from within the DNAstack interface. Read more about the background for this feature at their [blog](https://blog.dnastack.com/introducing-workflows-the-new-standard-in-cloud-bioinformatics-787a59b1d5c6) but here we also offer some information on what that looks like from a user point of view in a mini tutorial.

## While browsing DNAstack

While working within a project within the DNAstack interface, you can see an icon to manage the workflows associated with your project. 

![dnastack project0](/assets/images/docs/dnastack/dnastack_projects_0.png)

After clicking on that, you can see a list of the workflows associated with your project. Click on the button on the upper right to create a new workflow.

![dnastack project1](/assets/images/docs/dnastack/dnastack_projects_1.png)

When creating a workflow, you can work from scratch or import a workflow from Dockstore.

![dnastack project2](/assets/images/docs/dnastack/dnastack_projects_2.png)
![dnastack project3](/assets/images/docs/dnastack/dnastack_projects_3.png)

After selecting the workflow and selecting a version, you will see the contents of the workflow. You will need to make sure that runtime steps specify cpu, memory, and possibly disk requirements (highlighted in an example below) in order to successfully import into DNAstack. Note that you may also get an error if the workflow has already been imported into DNAstack.

![dnastack project4](/assets/images/docs/dnastack/dnastack_projects_4.png)

If the import was successful after hitting the "Import" button you will see the regular DNAstack interface which will let you specify inputs and other parameters in order to run the workflow just like any other workflow in DNAstack.  

![dnastack project5](/assets/images/docs/dnastack/dnastack_projects_5.png)



## While browsing Dockstore

When browsing WDL workflows from within Dockstore, you will see a "Launch-With" icon on the right.

![WDL workflow](/assets/images/docs/dnastack/dnastack_from_dockstore1.png)

If not logged into DNAstack, you will be prompted to login. Otherwise or after login, you will be presented with the following screen. 

![WDL workflow import](/assets/images/docs/dnastack/dnastack_from_dockstore2.png)

You will need to pick a version of your workflow to import and a project to import it into.
Then hit the button to "Import" and continue from within the DNAstack interface to run your workflow. 
Note that as with the above approach, you will want to double-check that the workflow specifies a runtime environment (docker, cpu, memory, and disks) if you have trouble importing the workflow and that the workflow has not been imported before. 


## Limitations
0. While we support launching of WDL workflows, tools as listed in Dockstore are currently not supported.
0. DNAstack does not currently support HTTP(S) or file-path based imports.  Importing a workflow with those imports will result in an error.  See [cromwell imports docs](https://cromwell.readthedocs.io/en/develop/Imports/) for more info about imports.

## See Also

* [AWS Batch](/advanced-topics/aws-batch/)
* [Azure Batch](/advanced-topics/azure-batch/)
* [DNAnexus Launch With](dnanexus-launch-with/)
* [FireCloud Launch With](firecloud-launch-with/)
* [Terra Launch With](terra-launch-with/)

