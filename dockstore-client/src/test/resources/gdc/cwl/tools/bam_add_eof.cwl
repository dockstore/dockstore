#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/bam_add_eof
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam_path
    type: File
    inputBinding:
      prefix: --bam_path

  - id: picard_validation_path
    type: File
    inputBinding:
      prefix: --picard_validation_path

  - id: uuid
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: output_bam
    type: File
    description: "The BAM file with EOF"
    outputBinding:
      glob: $(inputs.bam_path.path.split('/').slice(-1)[0])

  - id: log
    type: File
    description: "python log file"
    outputBinding:
      glob: $(inputs.uuid + "_bam_add_eof.log")

  - id: output_sqlite
    type: File
    description: "sqlite file"
    outputBinding:
      glob: $(inputs.uuid + ".db")

baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.5/site-packages/bam_add_eof/main.py"]
