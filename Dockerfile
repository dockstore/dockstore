FROM postgres:9.4

# install deps
RUN echo "deb http://http.debian.net/debian jessie-backports main" >> /etc/apt/sources.list
RUN apt-get update && apt-get install -y maven openjdk-8-jdk

# build app
RUN mkdir /gitroot
COPY checkstyle.xml /gitroot/
COPY dependency-reduced-pom.xml /gitroot/
COPY findbugs-exclude.xml /gitroot/
COPY pom.xml /gitroot/
COPY docker-entrypoint.sh /gitroot/
COPY src /gitroot/src
# now build this
RUN cd /gitroot && mvn clean install
RUN chmod a+x /gitroot/docker-entrypoint.sh

# default command launches daemons
CMD /gitroot/docker-entrypoint.sh
