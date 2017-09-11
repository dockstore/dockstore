#!/usr/bin/env cwl-runner
#
# Author Jeltje van Baren jeltje.van.baren@gmail.com

cwlVersion: v1.0
class: CommandLineTool
baseCommand: ["--h", "ls"]

doc: "Mutect 1.1.5"

hints:
  DockerRequirement:
    dockerPull: quay.io/jeltje/mutect

requirements:
  - class: InlineJavascriptRequirement
  - class: ResourceRequirement
    coresMin: 8
    ramMin: 60000
  - class: InitialWorkDirRequirement
    listing:
      - $(inputs.input_tumor)

inputs:


  input_normal:
    type: File
    doc: |
      bam input for control
    inputBinding:
      position: 2
      prefix: --input_file:normal
    secondaryFiles:
      - .bai

  input_tumor:
    type: File
    doc: |
      bam input for tumor
    inputBinding:
      position: 2
      prefix: --input_file:tumor
    secondaryFiles:
      - .bai

  out_vcf:
    type: string
    doc: |
      name of output file
    inputBinding:
      position: 2
      prefix: --vcf

  ncpus:
    type: int?
    inputBinding:
      position: 2
      prefix: --ncpus

  m:
    type: string?
    doc: |
      location of mutect.jar, default /opt/muTect-1.1.5.jar
    inputBinding:
      position: 2
      prefix: -m

  coverage_file:
    type: File?
    doc: |
      Coveragefile
    inputBinding:
      position: 2
      prefix: --coverage_file

  fraction_contamination:
    type: float?
    doc: |
      fraction of contamination, default None
    inputBinding:
      position: 2
      prefix: --fraction_contamination

  fraction_contamination_file:
    type: File?
    doc: |
      fraction of contamination_file
    inputBinding:
      position: 2
      prefix: --fraction_contamination_file

  tumor_lod:
    type: float?
    doc: |
      tumor LOD, default 6.3
    inputBinding:
      position: 2
      prefix: --tumor_lod

  init_tumor_lod:
    type: float?
    doc: |
      initial tumor LOD, default 4.0
    inputBinding:
      position: 2
      prefix: --initial_tumor_lod

  no_clean:
    type: boolean?
    doc: |
      do not remove intermediate files, default False
    inputBinding:
      position: 2
      prefix: --no-clean

  java:
    type: string?
    doc: |
      location of java program, default /usr/bin/java
    inputBinding:
      position: 2
      prefix: --java

  b:
    type: int?
    doc: |
        parallel block size, default 50M
    inputBinding:
      position: 2
      prefix: -b

  dict-jar:
    type: string?
    doc: |
      location of picard CreateSequenceDictionary.jar , default /opt/picard/CreateSequenceDictionary.jar
    inputBinding:
      position: 2
      prefix: --dict-jar


outputs:

  mutations:
    type: File
    outputBinding:
      glob: "tumor.bam"



arguments:

  - prefix: --reference-sequence
    valueFrom: $("/genome.fa")

  - prefix: --dbsnp
    valueFrom: $("/dbSNP.vcf")

  - prefix: --cosmic
    valueFrom: $("/cosmic.vcf")


