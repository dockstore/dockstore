#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/curl:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: output
    type: string
    inputBinding:
      prefix: --output

  - id: url
    type: string
    inputBinding:
      prefix: --url

outputs:
  - id: output_file
    type: File
    outputBinding:
      glob: $(inputs.output)
      
baseCommand: [curl]
