#############################################################
# Dockerfile to build a helloworld SeqWare workflow container
# Based on SeqWare that knows how to read/write to S3
#############################################################

# Set the base image to SeqWare
FROM seqware/seqware_whitestar:1.1.1

# File Author / Maintainer
MAINTAINER Brian O'Connor <briandoconnor@gmail.com>

# Setup packages
USER root
RUN apt-get -m update && apt-get install -y apt-utils tar git curl nano wget dialog net-tools build-essential time

# LEFT OFF WITH: need to install the s3 command line tools

# Build the workflow
COPY workflow-HelloWorld /home/seqware/workflow-HelloWorld
RUN chown -R seqware /home/seqware/workflow-HelloWorld
USER seqware
WORKDIR /home/seqware/workflow-HelloWorld/
RUN mvn clean install
CMD ["/bin/bash"]
