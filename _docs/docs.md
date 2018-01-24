---
title: Documentation
permalink: /docs/
---

# About the Dockstore

The Dockstore concept is simple, provide a place where users can share tools encapsulated in Docker and described with the [Common Workflow Language](http://common-workflow-language.github.io/) (CWL), an emerging standard used by the [GA4GH](https://genomicsandhealth.org/) Containers and Workflow working group. This enables scientists, for example, to share analytical tools in a way that makes them machine readable and runnable in a variety of environments.  While the Dockstore is focused on serving researchers in the biosciences the combination of Docker + CWL can be used by anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.

Dockstore also attempts to work with new and alternative languages/standards such as [WDL](https://github.com/broadinstitute/wdl) as an alternative to CWL and the [GA4GH Tool Registry](https://github.com/ga4gh/tool-registry-schemas) standard. We are also working on the task and workflow standards developing at the GA4GH.

## Built with Quay.io and GitHub ##

Docker repositories, like [Docker Hub](https://hub.docker.com/),  [Quay.io](https://quay.io/) and [GitLab](https://gitlab.com), and source control repositories like [GitHub](http://github.com), [Bitbucket](https://bitbucket.org/), and [GitLab](https://gitlab.com), provide much of the infrastructure we need.  They allow users build, publish, and share both public and private Docker images.  However, the services lack standardized ways of describing how to invoke tools contained within Docker containers.  The CWL standard has defined a way to define the inputs, parameterizations, and outputs of tools using a YAML-formatted file.  Together, these resources provide the necessary tools to share analytical tools in a highly portable way, a key concern for the scientific community.

![Overview]({{"/assets/images/docs/dockstore_logos.png" | relative_url }})

## Best Practices

First and foremost, the Dockstore has no requirements for what you register provided:

0. you can host the Docker image on Quay.io (and others in the future) which is linked to [GitHub](http://github.com) for automated building
0. you have a corresponding `Dockstore.cwl` in CWL format that describes how to call the tools inside your Docker image

You can also mix and match, substituting WDL for CWL, or Docker Hub for Quay.io.

Over time, we find "skinny" Docker images, those with single tools installed in them, are more helpful for extending and building new workflows with.  That being said, "fat" Docker containers, which include multiple tools and even full workflows with frameworks like [SeqWare](http://seqware.io) or [Galaxy](https://galaxyproject.org/), can have their place as well.  Projects like the ICGC [PanCancer Analysis of Whole Genomes](https://dcc.icgc.org/pcawg) (PCAWG) made use of "fat" Docker containers that had complex workflows that fully encapsulated alignment and variant calling.  The self-contained nature of these Docker containers allowed for mobility between a wide variety of environments and greatly simplified the setup of these pipelines across a wide variety of HPC and cloud environments. Either approach works for the Dockstore so long as you can describe the tool or workflow inside the Docker container as a CWL-defined tool (which you can for most things).

## Promoting Standards

We hope Dockstore provides a reference implementation for tool sharing in the sciences.  The Dockstore is essentially a proof of concept designed as a starting point for two activities that we hope can results in community
standards within the GA4GH:

* a best practices guide for describing tools in Docker containers with CWL
* a minimal web service standard for registering, searching and describing CWL-annotated Docker containers that can be federated and indexed by multiple websites similar to [Maven Central](http://search.maven.org/)

## Building a Community

Several large projects in the Biosciences, specifically cancer sequencing projects such as PCWAG, have expressed interest in registering tools and workflows in the Dockstore system.  We hope this work can spawn the registration of a large number of high-quality tools in the system.

## Future Plans

We plan on expanding the Dockstore in several ways over the coming months.  Please see our [issues page](https://github.com/CancerCollaboratory/dockstore/issues) for details and discussions.
