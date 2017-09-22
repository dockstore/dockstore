#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InitialWorkDirRequirement
    listing:
      - entryname: $(inputs.fasta.basename)
        entry: $(inputs.fasta)
      - entryname: $(inputs.fasta_dict.basename)
        entry: $(inputs.fasta_dict)
      - entryname: $(inputs.fasta_index.basename)
        entry: $(inputs.fasta_index)
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: fasta
    type: File
    format: "edam:format_1929"

  - id: fasta_dict
    type: File

  - id: fasta_index
    type: File

outputs:
  - id: output
    type: File
    format: "edam:format_1929"
    outputBinding:
      glob: $(inputs.fasta.basename)
    secondaryFiles:
      - ^.dict
      - .fai

baseCommand: "true"
