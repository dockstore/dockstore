#!/usr/bin/env cwl-runner

cwlVersion: cwl:draft-3

class: CommandLineTool

requirements:
  - class: InlineJavascriptRequirement
  - class: CreateFileRequirement
    fileDef:
      - filename: $(inputs.fasta_path.basename)
        fileContent: $(inputs.fasta_path)
      - filename: $(inputs.fasta_amb_path.basename)
        fileContent: $(inputs.fasta_amb_path)
      - filename: $(inputs.fasta_ann_path.basename)
        fileContent: $(inputs.fasta_ann_path)
      - filename: $(inputs.fasta_bwt_path.basename)
        fileContent: $(inputs.fasta_bwt_path)
      - filename: $(inputs.fasta_fai_path.basename)
        fileContent: $(inputs.fasta_fai_path)
      - filename: $(inputs.fasta_pac_path.basename)
        fileContent: $(inputs.fasta_pac_path)
      - filename: $(inputs.fasta_sa_path.basename)
        fileContent: $(inputs.fasta_sa_path)

inputs:
  - id: fasta_path
    type: File
  - id: fasta_amb_path
    type: File
  - id: fasta_ann_path
    type: File
  - id: fasta_bwt_path
    type: File
  - id: fasta_fai_path
    type: File
  - id: fasta_pac_path
    type: File
  - id: fasta_sa_path
    type: File

outputs:
  - id: output
    type: File
    outputBinding:
      glob: $(inputs.fasta_path.basename)
    secondaryFiles:
      - .amb
      - .ann
      - .bwt
      - .fai
      - .pac
      - .sa

baseCommand: []
