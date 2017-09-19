#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InitialWorkDirRequirement
    listing:
      - entryname: $(inputs.fasta.basename)
        entry: $(inputs.fasta)
      - entryname: $(inputs.fasta_amb.basename)
        entry: $(inputs.fasta_amb)
      - entryname: $(inputs.fasta_ann.basename)
        entry: $(inputs.fasta_ann)
      - entryname: $(inputs.fasta_bwt.basename)
        entry: $(inputs.fasta_bwt)
      - entryname: $(inputs.fasta_pac.basename)
        entry: $(inputs.fasta_pac)
      - entryname: $(inputs.fasta_sa.basename)
        entry: $(inputs.fasta_sa)
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: fasta
    type: File
    format: "edam:format_1929"

  - id: fasta_amb
    type: File

  - id: fasta_ann
    type: File

  - id: fasta_bwt
    type: File

  - id: fasta_pac
    type: File

  - id: fasta_sa
    type: File

outputs:
  - id: output
    type: File
    format: "edam:format_1929"
    outputBinding:
      glob: $(inputs.fasta.basename)
    secondaryFiles:
      - .amb
      - .ann
      - .bwt
      - .pac
      - .sa

baseCommand: "true"
