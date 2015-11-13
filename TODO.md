# New Features

This document will keep a list of features we want (not yet prioritized).

* support for hosting multiple Dockerfiles and Dockstore.cwl files in the same git repository
* support for distinct versions of `Dockstore.cwl` associated with each "tag" in Quay.io (currently the most recent `Dockstore.cwl` on the default git branch is used)
* unique, stable URL for each tool/version that can be used in publications
* integrate the [Launcher](https://github.com/CancerCollaboratory/dockstore-descriptor#dockstore-descriptor) into the Dockstore command line utility
* auto-conversion, if a user uploads a WDL auto-convert to CWL and vice versa(?) (Broad group may help with this)
* support for private repositories, groups, sharing settings, etc
* support for [EDAM ontology](http://edamontology.org/page) terms to describe input and output files (actually, this is beyond the scope of the Dockstore since it can be added into the CWL without the Dockstore really knowing)
* support for community tagging and star ratings for tools
* support for simple registration with URLs, DockerHub, Bitbucket, etc
* support for working with Quay.io repos that aren't auto-built (so you have to explicitly set the git repo used since we can't discover from Quay.io)
* CWL workflow registration in addition to individual tools
* work to promote a standardized web service API for sharing Docker-based tools through the GA4GH so other sites can register Docker images described with CWL and we can cross index, think [MavenCentral](http://search.maven.org/)
* contribute to a community best practices guide for tool wrapping in CWL/Docker
* publish Dockstore images for tools from the [PCAWG](https://dcc.igcg.org/pcawg) and other projects to help seed the Dockstore
* look at linking to tools defined by other standards such as [Galaxy Toolshed](https://toolshed.g2.bx.psu.edu/), [Elixir](https://elixir-registry.cbs.dtu.dk/) and WDL from the Broad
* examples of how to make workflows in CWL that bring together individual tools, and to scale up analysis using cloud orchestration systems like [Consonance](https://github.com/Consonance/)

