FROM eclipse-temurin:21.0.2_13-jdk-jammy

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
ARG galaxy_plugin_version=0.0.8
RUN apt-get install -y wget
RUN mkdir -p /root/.dockstore/language-plugins
RUN wget -P /root/.dockstore/language-plugins https://artifacts.oicr.on.ca/artifactory/collab-release/com/github/galaxyproject/dockstore-galaxy-interface/dockstore-galaxy-interface/${galaxy_plugin_version}/dockstore-galaxy-interface-${galaxy_plugin_version}.jar

# Include snakemake language plugin
ARG snakemake_plugin_version=0.0.5
RUN wget -P /root/.dockstore/language-plugins https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/snakemake-language-interface/${snakemake_plugin_version}/snakemake-language-interface-${snakemake_plugin_version}.jar


# Wipe them out, all of them, to reduce CVEs
RUN apt-get purge -y -- \
    *python* \
    wget \
    bzip2 \
    fonts-dejavu-core \
    fontconfig* \
    binutils* \
    cpio \
    *ssh* \
    && \
    apt-get -y autoremove

# healthcheck relies on curl, curl relies on part of ssh
# Install aide, file integrity verification
RUN apt update && \
     apt install curl cron aide aide-common -y --no-install-recommends && \
     aideinit && \
     apt-get clean && \
     rm -rf /var/lib/apt/lists/*
# update-aide.conf moves https://serverfault.com/questions/1111551/update-aide-conf-command-not-found
RUN aide --config /etc/aide/aide.conf --init
# Ignore these directories
RUN printf "\n!/var/log\n!/tmp/\n!/var/lib\n!/etc/aide\n" >> /etc/aide/aide.conf
# Add a script to send daily reports to dockstore-security lambda
RUN echo "#!/bin/bash\nset -e\n\nset -C\necho \""{\\\"aide-report\\\": {\\\"hostname\\\": \\\"\$\(hostname\)\\\", \\\"report\\\": \\\"\$\(aide -c /etc/aide/aide.conf -u\; cp /var/lib/aide/aide.db.new /var/lib/aide/aide.db\)\\\"}}"\" | curl -X POST https://api.dockstore-security.org/csp-report --data-binary @-" > /etc/cron.daily/aide
RUN chmod a+x /etc/cron.daily/aide
RUN rm /etc/cron.daily/apt-compat /etc/cron.daily/dpkg
RUN aide -c /etc/aide/aide.conf --update || true
RUN cp /var/lib/aide/aide.db.new /var/lib/aide/aide.db

CMD ["/home/init_webservice.sh"]

