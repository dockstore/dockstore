#!/usr/bin/env cwl-runner
#
# Authors: Thomas Yu, Ryan Spangler, Kyle Ellrott

cwlVersion: v1.0
class: CommandLineTool
baseCommand: [Integrate, fusion]

doc: "INTEGRATE: Fusion Quantification"

hints:

  DockerRequirement:
    dockerPull: docker.synapse.org/syn2813589/integrate

requirements:

  - class: InlineJavascriptRequirement
  - class: ResourceRequirement
    coresMin: 8
    ramMin: 60000

inputs:

  accepted:
    type: File
    inputBinding:
      position: 5
    secondaryFiles:
      - .bai
  
  unmapped:
    type: File
    inputBinding:
      position: 6
    secondaryFiles:
      - .bai

  o:
    type: string
    inputBinding:
      prefix: -bedpe
      position: 1

  index: Directory

outputs:

  integrate_fusions:
    type: File
    outputBinding:
      glob: $(inputs.o)

arguments:

  #reference.fasta
  - valueFrom: $(inputs.index.listing[0].path + "/Homo_sapiens.GRCh37.75.dna.primary_assembly.fa")
    position: 2
  #annotation.txt
  - valueFrom: $(inputs.index.listing[0].path + "/Homo_sapiens.GRCh37.75.txt")
    position: 3
  #Directory to INTEGRATE index files
  - valueFrom: $(inputs.index.listing[0].path)
    position: 4
