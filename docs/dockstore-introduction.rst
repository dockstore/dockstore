About Dockstore
===============

The Dockstore concept is simple, provide a place where users can share
tools encapsulated in Docker and described with the `Common Workflow
Language <http://common-workflow-language.github.io/>`__ (CWL) or
`Workflow Description Language <http://www.openwdl.org/>`__ (WDL),
workflow languages used by members of and APIS created by the
`GA4GH <https://genomicsandhealth.org/>`__ `Cloud Work
Stream <http://ga4gh.cloud/>`__. This enables scientists, for example,
to share analytical tools in a way that makes them machine readable and
runnable in a variety of environments. While the Dockstore is focused on
serving researchers in the biosciences the combination of Docker +
CWL/WDL can be used by anyone to describe the tools and services in
their Docker images in a standardized, machine-readable way.

Dockstore also attempts to work with new and alternative
languages/standards such as `Nextflow <https://www.nextflow.io/>`__ as
popular challengers to CWL and WDL. We also work on the `GA4GH Tool
Registry <https://github.com/ga4gh/tool-registry-schemas>`__ standard as
a way of sharing data with workflow platforms and partners. We are also
working with the task execution, workflow execution, and data transfer
standards developing at the GA4GH.

Built with Docker and Git
-------------------------

Docker repositories, like `Docker Hub <https://hub.docker.com/>`__,
`Quay.io <https://quay.io/>`__ and `GitLab <https://gitlab.com>`__, and
source control repositories like `GitHub <http://github.com>`__,
`Bitbucket <https://bitbucket.org/>`__, and
`GitLab <https://gitlab.com>`__, provide much of the infrastructure we
need. Docker repositories allow users to build, publish, and share both
public and private Docker images. However, the services lack
standardized ways of describing how to invoke tools contained within
Docker containers. Workflow descriptor languages provide standardised
ways to define the inputs, parameterizations, and outputs of tools in a
controlled way. Together, these resources provide the necessary tools to
share analytical tools in a highly portable way, a key concern for the
scientific community.

.. figure:: /assets/images/docs/Ways_to_get_into_Dockstore.png
   :alt: Overview

   Overview

Strategies
----------

You can register your tools and workflows on Dockstore in three broad
ways as depicted above.

A) Following our
   `tutorials <https://docs.dockstore.org//getting-started-with-docker/>`__,
   you can save your descriptors on GitHub, build your Docker image
   automatically on Quay.io, and have Dockstore reach out and index your
   tools

B) Dockstore can retrieve your workflow descriptors from GitHub and
   other source control methods. You are responsible for ensuring that
   your descriptors point at valid Docker images.

C) You will be able to use our new hosted workflows service to store
   tools and workflows directly on dockstore.org to quickly get started,
   prototype your ideas, and share workflows with a limited audience.

In all three cases, you will have an opportunity to clean-up and
configure your work before publishing to the rest of the world to see.

You can mix and match in a number of these approaches. For example, you
can go beyond our simple tutorials and automated approach to manually
register tools that point at locations like Docker Hub, Seven Bridges,
and Amazon ECR. You can substitute WDL and Nextflow for the descriptor
language for workflows. You might even be able to mix and match
descriptor languages eventually!

Over time, we find "skinny" Docker images, those with single tools
installed in them, are more helpful for extending and building new
workflows with. That being said, "fat" Docker containers, which include
multiple tools and even full workflows with frameworks like
`SeqWare <http://seqware.io>`__ or
`Galaxy <https://galaxyproject.org/>`__, can have their place as well.
Projects like the ICGC `PanCancer Analysis of Whole
Genomes <https://dcc.icgc.org/pcawg>`__ (PCAWG) made use of "fat" Docker
containers that had complex workflows that fully encapsulated alignment
and variant calling. The self-contained nature of these Docker
containers allowed for mobility between a wide variety of environments
and greatly simplified the setup of these pipelines across a wide
variety of HPC and cloud environments. Either approach works for the
Dockstore so long as you can describe the tool or workflow inside the
Docker container as a CWL/WDL-defined tool (which you can for most
things).

Promoting Standards
-------------------

We hope Dockstore provides a reference implementation for tool sharing
in the sciences. The Dockstore is essentially a living and evolving
proof of concept designed as a starting point for two activities that we
hope will result in community standards within the GA4GH:

-  a best practices guide for describing tools in Docker containers with
   CWL/WDL/Nextflow
-  a minimal web service standard for registering, searching and
   describing CWL-annotated Docker containers that can be federated and
   indexed by multiple websites similar to `Maven
   Central <https://search.maven.org/>`__

We also implement certain utilities such as file provisioning plugins
that support the GA4GH DOS (Data Object Service) standard or
command-line launchers that present a common interface across CWL and
WDL as almost a polyfill to demonstrate what we wish to use over time
natively.

Building a Community
--------------------

Several large projects in the Biosciences, specifically cancer
sequencing projects such as PCAWG, PrecisionFDA, the Broad Institute,
the University of California Santa Cruz, and Cancer IT at Sanger have
registered between 10 and 60 workflows each in Dockstore as of August
2018. We hope this work will aid the community and promote the
registration of a large number of high-quality workflows in the system.

Future Plans
------------

We plan on expanding the Dockstore in several ways over the coming
months. Please see our `issues
page <https://github.com/ga4gh/dockstore/issues>`__ for details and
discussions.