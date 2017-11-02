#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InitialWorkDirRequirement
    listing:
      - entryname: $(inputs.bam.basename)
        entry: $(inputs.bam)
      - entryname: $(inputs.bam_index.basename)
        entry: $(inputs.bam_index)
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam
    type: File
    format: "edam:format_2572"

  - id: bam_index
    type: File

outputs:
  - id: output
    type: File
    format: "edam:format_2572"
    outputBinding:
      glob: $(inputs.bam.basename)
    secondaryFiles:
      - ^.bai

baseCommand: "true"
