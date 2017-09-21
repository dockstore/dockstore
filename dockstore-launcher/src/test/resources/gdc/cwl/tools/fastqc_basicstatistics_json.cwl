#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/fastqc_to_json:1

class: CommandLineTool

inputs:
  - id: sqlite_path
    type: File
    inputBinding:
      prefix: --sqlite_path

outputs:
  - id: OUTPUT
    type: File
    outputBinding:
      glob: "fastqc.json"

baseCommand: [/usr/local/bin/fastqc_to_json]
