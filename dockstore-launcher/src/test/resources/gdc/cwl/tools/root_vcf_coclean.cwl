#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InitialWorkDirRequirement
    listing:
      - entryname: $(inputs.vcf.basename)
        entry: $(inputs.vcf)
      - entryname: $(inputs.vcf_index.basename)
        entry: $(inputs.vcf_index)
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: vcf
    type: File
    format: "edam:format_3016"

  - id: vcf_index
    type: File

outputs:
  - id: output
    type: File
    format: "edam:format_3016"
    outputBinding:
      glob: $(inputs.vcf.basename)
    secondaryFiles:
      - .tbi

baseCommand: "true"
