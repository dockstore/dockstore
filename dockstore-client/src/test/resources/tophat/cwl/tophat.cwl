#!/usr/bin/env cwl-runner
#
# Authors: Thomas Yu, Ryan Spangler, Kyle Ellrott

cwlVersion: v1.0
class: CommandLineTool
baseCommand: [tophat]

doc: "Tophat including fusion detection"

hints:
  DockerRequirement:
    dockerPull: quay.io/jeltje/tophat2

requirements:
  - class: InlineJavascriptRequirement
  - class: ResourceRequirement
    coresMin: 8
    ramMin: 60000

inputs:

  r:
    type: int?
    inputBinding:
      position: 2
      prefix: -r

  p:
    type: int?
    doc: |
      Change number of threads used
    inputBinding:
      position: 2
      prefix: -p

  mate-std-dev:
    type: int?
    inputBinding:
      position: 2
      prefix: --mate-std-dev

  max-intron-length:
    type: int?
    inputBinding:
      position: 2
      prefix: --max-intron-length

  fusion-min-dist:
    type: int?
    inputBinding:
      position: 2
      prefix: --fusion-min-dist

  no-novel-junc:
    type: boolean?
    doc: |
      Turn off junction and coverage search
    inputBinding:
      prefix: --no-novel-juncs
      position: 2

  fusion-anchor-length:
    type: int?
    inputBinding:
      position: 2
      prefix: --fusion-anchor-length

  fusion-ignore-chromosomes:
    type: string?
    inputBinding:
      position: 2
      prefix: --fusion-ignore-chromosomes

  fusion-search:
    type: boolean?
    doc: |
      Turn on fusion algorithm (tophat-fusion)
    inputBinding:
      prefix: --fusion-search
      position: 2

  keep-fasta-order:
    type: boolean?
    doc: |
      Keep ordering of fastq file
    inputBinding:
      prefix: --keep-fasta-order
      position: 2

  bowtie1:
    type: boolean?
    doc: |
      Use bowtie1
    inputBinding:
      prefix: --bowtie1
      position: 2

  no-coverage-search:
    type: boolean?
    doc: |
      Turn off coverage-search, which takes lots of memory and is slow
    inputBinding:
      prefix: --no-coverage-search
      position: 2

  fastq1:
    type: File
    inputBinding:
      position: 4

  fastq2:
    type: File
    inputBinding:
      position: 5

  o:
    type: string
    inputBinding:
      prefix: -o
      position: 1

  bowtie_index: Directory

outputs:

  tophatOut_fusions:
    type: File?
    outputBinding:
      glob: $(inputs.o+'/fusions.out')

  tophatOut_accepted_hits:
    type: File
    outputBinding:
      glob: $(inputs.o+'/accepted_hits.bam')

  tophatOut_unmapped:
    type: File
    outputBinding:
      glob: $(inputs.o+'/unmapped.bam')

arguments:
  - valueFrom: $(inputs.bowtie_index.listing[0].path + "/genome")
    position: 3
