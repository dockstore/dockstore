FROM eclipse-temurin:17.0.3_7-jdk-focal

# Wipe them out, all of them, to reduce CVEs
RUN apt-get purge -y -- *python*  && apt-get -y autoremove

# Update the APT cache
# Prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends
# Note locale settings seem redundant, temurin already has en_US.UTF-8 set
#    locales \
#    && apt-get clean \
#    && rm -rf /var/lib/apt/lists/* \
#    && localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
# ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# Copy the jar not ending in 's', to make sure we get don't get the one ending in 'sources'
COPY dockstore-webservice/target/dockstore-webservice*[^s].jar /home

RUN mkdir /dockstore_logs && chmod a+rx /dockstore_logs

# Include galaxy language plugin
ARG galaxy_plugin_version=0.0.7
RUN apt-get install -y wget
RUN mkdir -p /root/.dockstore/language-plugins
RUN wget -P /root/.dockstore/language-plugins https://artifacts.oicr.on.ca/artifactory/collab-release/com/github/galaxyproject/dockstore-galaxy-interface/dockstore-galaxy-interface/${galaxy_plugin_version}/dockstore-galaxy-interface-${galaxy_plugin_version}.jar

CMD ["/home/init_webservice.sh"]

