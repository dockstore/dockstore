FROM frolvlad/alpine-python3:latest

LABEL maintainer="marc.zimmermann@inf.ethz.ch"

RUN mkdir -p /data/input && mkdir /data/output && mkdir /scripts
COPY pipeline_code/complex_computation.py /scripts
COPY pipeline_code/line_counts.sh /scripts
