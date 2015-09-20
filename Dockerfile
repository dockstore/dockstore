FROM postgres:9.4

RUN mkdir /gitroot
COPY checkstyle.xml /gitroot/
COPY dependency-reduced-pom.xml /gitroot/
COPY findbugs-exclude.xml /gitroot/
COPY pom.xml /gitroot/
COPY docker-entrypoint.sh /gitroot/
COPY src /gitroot/src

# now build this
#RUN apt-get update && apt-get install -y software-properties-common python-software-properties
RUN echo "deb http://http.debian.net/debian jessie-backports main" >> /etc/apt/sources.list
RUN apt-get update && apt-get install -y maven openjdk-8-jdk
RUN cd /gitroot && mvn clean install

ENTRYPOINT ["/bin/sh", "-c"]

CMD /gitroot/docker-entrypoint.sh
