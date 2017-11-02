#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/bam_reheader:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam_path
    type: File
    format: "edam:format_2572"
    inputBinding:
      prefix: --bam_path

outputs:
  - id: output_bam
    type: File
    format: "edam:format_2572"
    outputBinding:
      glob: $(inputs.bam_path.basename)

  - id: log
    type: File
    outputBinding:
      glob: $(inputs.bam_path.basename + ".log")

baseCommand: [/usr/local/bin/bam_reheader]
