---
title: Language Support
permalink: /docs/user-tutorials/language-support/
---

Ideally, all features in Dockstore would be available in all languages. 
However, due to time constraints and gaps in our knowledge of different workflows, some features of Dockstore are not available in all languages. 

To help lay out what parts of Dockstore are available in which languages, we cover the following guide for what features are available on the Dockstore site and in the Dockstore command-line utility. 

| Feature                | CWL           | WDL   |
| ---------------------  | ------------- | ----- | 
| **GUI**                |               |       |
| Tool registration      | ✔             | ✔     | 
| Workflow registration  | ✔             | ✔     |  
| DAG Display            | ✔             | ✔     |  
| Launch-with Platforms  | N/A           | FireCloud<br>DNAStack |  
| **CLI**                |               |       |
| File Provisioning      | Local File System<br>HTTP<br>FTP<br>S3             | Local File System     | 
| Local workflow engines | cwltool<br>Bunny<br>Toil (experimental)              | Cromwell |
{: .table .table-striped .table-condensed}

<!-- &ast; Nextflow has preliminary support for workflow registration -->

{% include language-support.html %}
