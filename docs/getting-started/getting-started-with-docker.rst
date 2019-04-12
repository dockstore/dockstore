
.. note:: This is the first part of a tutorial series where you will create a tool called BAMStats and publish it onto Dockstore.

Getting Started with Docker
===========================

Tutorial Goals
--------------

-  Learn about Docker
-  Create a Docker image for a real tool
-  Create a tag locally
-  Test Docker image locally

Introduction to Docker
----------------------

Docker is a fantastic tool for creating light-weight containers to run
your tools. It gives you a fast, VM-like environment for Linux where you
can automatically install dependencies, make configurations, and setup
your tool exactly the way you want, just as you would on a "normal"
Linux host. You can then quickly and easily share these Docker images
with the world using registries like Quay.io (indexed by Dockstore),
Docker Hub, and GitLab.

Here we will go through a simple representative example. The end-product
is a Dockerfile for a BAMStats tool stored in a supported Git
repository.

Create a new repository
-----------------------

See the
`dockstore-tool-bamstats <https://github.com/CancerCollaboratory/dockstore-tool-bamstats>`__
repository on GitHub which we created as an example. This is linked to
the Quay.io repository at
`dockstore-tool-bamstats <https://quay.io/repository/collaboratory/dockstore-tool-bamstats>`__.

For the rest of this tutorial, you may wish to work in your own
repository with your own tool or "fork" the repository above into your
own GitHub account.

With a repository established in GitHub, the next step is to create the
Docker image with BAMStats correctly installed.

Creating a Dockerfile
---------------------

We will create a Docker image with BAMStats and all of its dependencies
installed. To do this we must create a ``Dockerfile``. Here's my sample
`Dockerfile <https://github.com/CancerCollaboratory/dockstore-tool-bamstats/blob/develop/Dockerfile>`__:

.. code:: dockerfile

    #############################################################
    # Dockerfile to build a sample tool container for BAMStats
    #############################################################

    # Set the base image to Ubuntu
    FROM ubuntu:14.04

    # File Author / Maintainer
    MAINTAINER Brian OConnor <briandoconnor@gmail.com>

    # Setup packages
    USER root
    RUN apt-get -m update && apt-get install -y wget unzip openjdk-7-jre zip

    # get the tool and install it in /usr/local/bin
    RUN wget -q http://downloads.sourceforge.net/project/bamstats/BAMStats-1.25.zip
    RUN unzip BAMStats-1.25.zip && \
        rm BAMStats-1.25.zip && \
        mv BAMStats-1.25 /opt/
    COPY bin/bamstats /usr/local/bin/
    RUN chmod a+x /usr/local/bin/bamstats

    # switch back to the ubuntu user so this tool (and the files written) are not owned by root
    RUN groupadd -r -g 1000 ubuntu && useradd -r -g ubuntu -u 1000 ubuntu
    USER ubuntu

    # by default /bin/bash is executed
    CMD ["/bin/bash"]

This Dockerfile has a lot going on in it. There are good tutorials
online about the details of a Dockerfile and its syntax. An excellent
resource is the Docker website itself, including the `Best practices for
writing
Dockerfiles <https://docs.docker.com/engine/userguide/eng-image/dockerfile_best-practices/>`__
webpage. I'll highlight some sections below:

.. code:: dockerfile

    FROM ubuntu:14.04

This uses the ubuntu 14.04 base distribution. How do I know to use
``ubuntu:14.04``? This comes from either a search on Ubuntu's home page
for their "official" Docker images or you can simply go to
`DockerHub <http://hub.docker.com>`__ or `Quay <http://quay.io>`__ and
search for whatever base image you like. You can extend anything you
find there. So if you come across an image that contains most of what
you want, you can use it as the base here. Just be aware of its source:
I tend to stick with "official", basic images for security reasons.

.. code:: dockerfile

    MAINTAINER Brian OConnor <briandoconnor@gmail.com>

You should include your name and contact information.

.. code:: dockerfile

    USER root
    RUN apt-get -m update && apt-get install -y wget unzip openjdk-7-jre zip
    RUN wget -q http://downloads.sourceforge.net/project/bamstats/BAMStats-1.25.zip
    RUN unzip BAMStats-1.25.zip && \
        rm BAMStats-1.25.zip && \
        mv BAMStats-1.25 /opt/

This switches to the ``root`` user to perform software installs. It
downloads BAMStats, unzips it, and installs it in the correct location,
``/opt``.

**This is why Docker is so powerful.** On HPC systems the above process
might take days or weeks of working with a sys admin to install
dependencies on all compute nodes. Here I can control and install
whatever I like inside my Docker image - correctly configuring the
environment for my tool and avoiding the time to set up these
dependencies in the places I want to run. This greatly simplifies the
install process for other users that you share your tool with as well.

.. code:: dockerfile

    COPY bin/bamstats /usr/local/bin/
    RUN chmod a+x /usr/local/bin/bamstats

This copies the local helper script ``bamstats`` from the git checkout
directory to ``/usr/local/bin``. This is an important example; it shows
how to use ``COPY`` to copy files in the git directory structure to
inside the Docker image. After copying to ``/usr/local/bin`` the script
is made runnable by all users.

.. code:: dockerfile

    RUN groupadd -r -g 1000 ubuntu && useradd -r -g ubuntu -u 1000 ubuntu
    USER ubuntu

    # by default /bin/bash is executed
    CMD ["/bin/bash"]

The user ``ubuntu`` is created and switched to in order to make file
ownership easier and the default command for this Docker image is set to
``/bin/bash`` which is a typical default.

An important thing to note is that this ``Dockerfile`` only scratches
the surface. Take a look at `Best practices for writing
Dockerfiles <https://docs.docker.com/engine/userguide/eng-image/dockerfile_best-practices/>`__
for a really terrific in-depth look at writing Dockerfiles.

Read more on the development process at
`https://docs.docker.com <https://docs.docker.com/>`__. For information
on building your Docker image on Quay.io we recommend their
`tutorial <https://quay.io/tutorial/>`__.

Building Docker Images
~~~~~~~~~~~~~~~~~~~~~~

Now that you've created the ``Dockerfile``, the next step is to build
the image. The docker command line is used for this:

::

    $> docker build -t quay.io/collaboratory/dockstore-tool-bamstats:1.25-3 .

The ``.`` is the path to the location of the Dockerfile, which is in the
same directory here. The ``-t`` parameter is the "tag" that this Docker
image will be called locally when it's cached on your host. A few things
to point out, the ``quay.io`` part of the tag typically denotes that
this was built on Quay.io (which we will see in a later section). I'm
manually specifying this tag so it will match the Quay.io-built version.
This allows me to build and test locally then, eventually, switch over
to the quay.io-built version. The next part of the tag,
``collaboratory/dockstore-tool-bamstats``, denotes the name of the tool
which is derived from the organization and repository name on Quay.io.
Finally ``1.25-3`` denotes a version string, typically you want to sync
that with releases on GitHub.

The tool should build normally and should exit without errors. You
should see something like:

::

    Successfully built 01a7ccf55063

Check that the tool is now in your local Docker image cache:

::

    $> docker images | grep bamstats
    quay.io/collaboratory/dockstore-tool-bamstats   1.25-3  01a7ccf55063   2 minutes ago   538.3 MB

Great! This looks fine!

Testing the Docker Image Locally
--------------------------------

OK, so you've built the image and created a tag. Now what?

The next step will be to test the tool directly via Docker to ensure
that your ``Dockerfile`` is valid and correctly installed the tool. If
you were developing a new tool there might be multiple rounds of
``docker build``, followed by testing with ``docker run`` before you get
your Dockerfile right. Here I'm executing the Docker image, launching it
as a container (make sure you launch on a host with at least 8GB of RAM
and dozens of GB of disk space!):

::

    $> docker run -it -v `pwd`:/home/ubuntu --user `echo $UID`:1000 quay.io/collaboratory/dockstore-tool-bamstats:1.25-3 /bin/bash

.. note:: This command expects your UID to be 1000. If it is not, you
    need to add ``--user <your-id>:1000``.

You'll be dropped into a bash shell which works just like the Linux
environments you normally work in. I'll come back to what ``-v`` is
doing in a bit. The goal now is to exercise the tool and make sure it
works as you expect. BAMStats is a very simple tool and generates some
reports and statistics for a BAM file. Let's run it on some test data
from the 1000 Genomes project:

::

    # this is inside the running Docker container
    $> cd /home/ubuntu
    $> wget ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/phase3/data/NA12878/alignment/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    # if the above doesn't work here's an alternative location
    $> wget https://s3.amazonaws.com/oconnor-test-bucket/sample-data/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    $> /usr/local/bin/bamstats 4 NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam

What's really going on here? The ``bamstats`` command above is a simple
script I wrote to make it easier to call BAMStats. This is what I used
the ``COPY`` command to move into the Docker image via the Dockerfile.
Here's the script's contents:

::

    #!/bin/bash
    set -euf -o pipefail

    java -Xmx$1g -jar /opt/BAMStats-1.25/BAMStats-1.25.jar -i $2 -o bamstats_report.html -v html
    zip -r bamstats_report.zip bamstats_report.html bamstats_report.html.data
    rm -rf bamstats_report.html bamstats_report.html.data

You can see it just executes the BAMStats jar - passing in the GB of
memory and the BAM file while collecting the output HTML report as a zip
file followed by cleanup.

.. note:: 
    Notice how the output is written to whatever the current
    directory is. This is the correct directory to put your output in since
    the CWL tool described later assumes that outputs are all located in the
    current working directory that it executes your command in.

The ``-v`` parameter used earlier is mounting the current working
directory into ``/home/ubuntu`` which was the directory we worked in
when running ``/usr/local/bin/bamstats`` above. The net effect is when
you exit the Docker container (with command ``exit`` or pressing
``ctrl + d``), you're left with a ``bamstats_report.zip`` file in the
current directory. This is a key point, it shows you how files are
retrieved from inside a Docker container.

You can now unzip and examine the ``bamstats_report.zip`` file on your
computer to see what type of reports are created by this tool. For
example, here's a snippet:

.. figure:: /assets/images/docs/report.png
   :alt: Sample report

   Sample report

Rather than interactively working with the image, you could also run
your Docker image from the command-line.

::

    $> wget ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/phase3/data/NA12878/alignment/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam
    $> docker run -w="/home/ubuntu" -it -v `pwd`:/home/ubuntu --user `echo $UID`:1000 quay.io/collaboratory/dockstore-tool-bamstats:1.25-3 bamstats 4 NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam

In the next section, we will demonstrate how the command-line and input
file can be parameterized and constructed via CWL.

Next Steps
----------

**You could stop here!** However, what you lose is a standardized way to
describe how to run your tool. That's what descriptor languages and
Dockstore provide. We think it's valuable and there's an increasing
number of tools and workflows designed to work with various descriptor
languages so there are benefits to not just stopping here.

There are three descriptor languages available on Dockstore. Follow the
links to get an introduction. 

- :doc:`CWL <getting-started-with-cwl>`
- :doc:`WDL <getting-started-with-wdl>`
- :doc:`Nextflow <getting-started-with-nextflow>`
