#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: ubuntu:xenial-20161010
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: input
    type: File
    inputBinding:
      position: 0

outputs:
  - id: fasta_amb
    type: File
    outputBinding:
      glob: "*.amb"

  - id: fasta_ann
    type: File
    outputBinding:
      glob: "*.ann"

  - id: fasta_bwt
    type: File
    outputBinding:
      glob: "*.bwt"

  - id: fasta_pac
    type: File
    outputBinding:
      glob: "*.pac"

  - id: fasta_sa
    type: File
    outputBinding:
      glob: "*.sa"

baseCommand: [tar, xf]
