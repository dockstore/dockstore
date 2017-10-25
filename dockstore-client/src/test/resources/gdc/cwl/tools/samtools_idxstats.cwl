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
    secondaryFiles:
      - ^.bai

outputs:
  - id: OUTPUT
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".idxstats")

stdout: $(inputs.INPUT.nameroot + ".idxstats")

baseCommand: [/usr/local/bin/samtools, idxstats]
