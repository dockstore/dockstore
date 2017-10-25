#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/samtools:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: INPUT
    type: File
    format: "edam:format_2572"
    inputBinding:
      position: 0

outputs:
  - id: OUTPUT
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".flagstat")

stdout: $(inputs.INPUT.nameroot + ".flagstat")

baseCommand: [/usr/local/bin/samtools, flagstat]
