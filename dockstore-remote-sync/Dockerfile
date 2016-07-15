FROM postgres:9.5
# FROM ubuntu:14.04
# RUN apt-get update && apt-get upgrade -y
# RUN apt-get update && apt-get install -y wget
# install postgres9.5
# RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ `lsb_release -cs`-pgdg main" >> /etc/apt/sources.list.d/pgdg.list
# RUN wget https://www.postgresql.org/media/keys/ACCC4CF8.asc -O - | sudo apt-key add -
# RUN apt-get update && apt-get -y install postgresql postgresql-contrib


RUN apt-get update && apt-get install -y \
	libpq-dev \
	python-pip \
	python-psycopg2 \
	&& rm -rf /var/lib/apt/lists/*

RUN pip install Flask


COPY fetchDataFromDockstore.py /fetchDataFromDockstore.py
COPY sever.py /sever.py
COPY run.sh /run.sh
RUN chmod a+x /run.sh
EXPOSE 5000
CMD /run.sh
