#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/remove_duplicate_fastq_matepair
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: fastq_path
    type: File
    inputBinding:
      prefix: --fastq_path

  - id: uuid
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: output_fastq
    type: File
    outputBinding:
      glob: $(inputs.fastq_path.path.split('/').slice(-1)[0])

  - id: log
    type: File
    description: "python log file"
    outputBinding:
      glob: $(inputs.uuid + "_remove_duplicate_fastq_matepair_main.log")

  - id: output_sqlite
    type: File
    description: "sqlite file"
    outputBinding:
      glob: $(inputs.uuid + ".db")

baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.5/site-packages/remove_duplicate_fastq_matepair/main.py"]
