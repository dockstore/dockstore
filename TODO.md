# New Features

This document will keep a (relatively) prioritized list of features we want.

* work to promote a standardized web service API for sharing Docker-based tools through the GA4GH so other sites can register Docker images described with CWL and we can cross index, think [MavenCentral](http://search.maven.org/)
* publish Dockstore images for tools from the [PCAWG](https://dcc.igcg.org/pcawg) and other projects to help seed the Dockstore
* look at linking to tools defined by other standards such as [Galaxy Toolshed](https://toolshed.g2.bx.psu.edu/) and [Elixir](https://elixir-registry.cbs.dtu.dk/)
* support for hosting multiple Dockerfiles and Dockstore.cwl files in the same git repository
* support for distinct versions of `Dockstore.cwl` associated with each "tag" in Quay.io (currently the most recent `Dockstore.cwl` on the default git branch is used)
* support for [EDAM ontology](http://edamontology.org/page) terms to describe input and output files
* support for community tagging and star ratings for tools
* CWL workflow registration in addition to individual tools
* examples of how to make workflows in CWL that bring together individual tools, and to scale up analysis using cloud orchestration systems like [Consonance](https://github.com/Consonance/)
* support for private repositories, groups, sharing settings, etc
* unique, stable URL for each tool/version that can be used in publications
* integrate the [Launcher](https://github.com/CancerCollaboratory/dockstore-descriptor#dockstore-descriptor) into the Dockstore command line utility
