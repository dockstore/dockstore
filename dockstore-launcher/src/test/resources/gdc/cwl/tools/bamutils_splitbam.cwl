#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

description: |
  Usage:  cwl-runner <this-file-path> --bam_path <bam-path> --uuid <uuid-string>
  Options:
    --bam_path       Generate readgroup BAMs from BAM file
    --uuid           UUID for log file and sqlite db file

requirements:
  - $import: envvar-global.cwl
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/bamutil_tool

class: CommandLineTool

inputs:
  - id: "#input_bam"
    type: File
    inputBinding:
      prefix: --bam_path

  - id: "#uuid"
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: "#output_bam"
    type:
      type: array
      items: File
    description: "The readgroup BAM{s}"
    outputBinding:
      glob: split.*.bam

  - id: "#log"
    type: File
    description: "python log file"
    outputBinding:
      glob: $(inputs.uuid + "_splitbam.log")

baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.4/site-packages/bamutil_tool/main.py", "--tool_name", "splitbam"]
