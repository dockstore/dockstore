#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/bam_readgroup_to_json:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: INPUT
    type: File
    format: "edam:format_2572"
    inputBinding:
      prefix: --bam_path

  - id: MODE
    type: string
    default: strict
    inputBinding:
      prefix: --mode

outputs:
  - id: OUTPUT
    format: "edam:format_3464"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*.json"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

  - id: log
    type: File
    outputBinding:
      glob: "output.log"

baseCommand: [/usr/local/bin/bam_readgroup_to_json]
