#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

description: |
  Usage:  cwl-runner <this-file-path> ...
  Options:
    --input_bam      Generate metrics from BAM file
    --reference      Genome for metrics
    --uuid           UUID for log file and sqlite db file

requirements:
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/picard_tool

class: CommandLineTool

inputs:
  - id: bam_path
    type: File
    inputBinding:
      prefix: --bam_path

  - id: readgroup_json_path
    type: File
    inputBinding:
      prefix: --readgroup_json_path

  - id: bam_library_kit_json_path
    type: File
    inputBinding:
      prefix: --bam_library_kit_json_path

  - id: orig_bam_name
    type: string
    inputBinding:
      prefix: --outbam_name

  - id: reference_fasta_path
    type: File
    inputBinding:
      prefix: --reference_fasta_path

  - id: uuid
    type: string
    inputBinding:
      prefix: --uuid
      
outputs:
  - id: log
    type: File
    outputBinding:
      glob: $(inputs.uuid + "_picard_CalculateHsMetrics_gdc.log")

  - id: hs_metrics
    type: File
    outputBinding:
      glob: $("picard_calculatehsmetrics_"+ inputs.bam_path.path.split('/').slice(-1)[0].slice(0,-4))

  - id: output_sqlite
    type: File
    description: "sqlite file"
    outputBinding:
      glob: $(inputs.uuid + ".db")


baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python", "/home/ubuntu/.virtualenvs/p3/lib/python3.4/site-packages/picard_tool/main.py", "--tool_name", "CalculateHsMetrics_gdc"]
