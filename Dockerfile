FROM postgres:9.4

RUN mkdir /gitroot
COPY checkstyle.xml /gitroot/
COPY dependency-reduced-pom.xml /gitroot/
COPY findbugs-exclude.xml /gitroot/
COPY pom.xml /gitroot/
COPY docker-entrypoint.sh /gitroot/
COPY src /gitroot/src

# now build this
RUN cd /gitroot/ && apt-get update && apt-get install -y maven openjdk-7-jdk
RUN cd /gitroot && mvn clean install

ENTRYPOINT ["/bin/sh", "-c"]

CMD /gitroot/docker-entrypoint.sh
