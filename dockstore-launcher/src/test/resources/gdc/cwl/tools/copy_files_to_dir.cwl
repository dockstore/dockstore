#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: ubuntu:xenial-20160923.1
  - class: InlineJavascriptRequirement
  - class: ShellCommandRequirement

class: CommandLineTool

inputs:
  - id: fasta
    type: File

  - id: fasta_amb
    type: File

  - id: fasta_ann
    type: File

  - id: fasta_bwt
    type: File

  - id: fasta_fai
    type: File

  - id: fasta_pac
    type: File

  - id: fasta_sa
    type: File

outputs:
  - id: output
    type: File
    outputBinding:
      glob: $(inputs.fasta.basename)
    secondaryFiles:
      - .amb
      - .ann
      - .bwt
      - .fai
      - .pac
      - .sa

arguments:
  - valueFrom: |
      ${
         var cmd = "cp " + inputs.fasta.path + " " +  inputs.fasta_amb.path + " " + inputs.fasta_ann.path + " "
                         + inputs.fasta_bwt.path + " " + inputs.fasta_fai.path + " " + inputs.fasta_pac.path + " "
                         + inputs.fasta_sa.path + " .";
         return cmd
      }
    shellQuote: false

baseCommand: []
