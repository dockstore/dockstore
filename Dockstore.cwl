#!/usr/bin/env cwl-runner
#
# Authors: Denis Yuen 

#!/usr/bin/env cwl-runner
class: CommandLineTool

description: |
    Dockstore

requirements:
  - class: ExpressionEngineRequirement
    requirements:
      - class: DockerRequirement
        dockerPull: commonworkflowlanguage/nodejs-engine
  - class: DockerRequirement
    dockerFile: |
FROM postgres:9.4

# Install Java.
RUN \
  apt-get update && apt-get install -y software-properties-common && \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" > /etc/apt/sources.list.d/webupd8team-java.list && \
  echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" >> /etc/apt/sources.list.d/webupd8team-java.list && \
  apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886 && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# install deps
RUN echo "deb http://http.debian.net/debian jessie-backports main" >> /etc/apt/sources.list
RUN apt-get update && apt-get install -y maven

# build app
RUN mkdir /gitroot
COPY checkstyle.xml /gitroot/
COPY dependency-reduced-pom.xml /gitroot/
COPY findbugs-exclude.xml /gitroot/
COPY pom.xml /gitroot/
COPY docker-entrypoint.sh /gitroot/
COPY dockstore-webservice /gitroot/dockstore-webservice
COPY swagger-java-client /gitroot/swagger-java-client
COPY dockstore-client /gitroot/dockstore-client
# now build this
RUN cd /gitroot && mvn clean install
RUN chmod a+x /gitroot/docker-entrypoint.sh

# default command launches daemons
CMD /gitroot/docker-entrypoint.sh

baseCommand: ["java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml"]
