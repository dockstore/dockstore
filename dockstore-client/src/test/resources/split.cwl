#!/usr/bin/env cwl-runner

class: CommandLineTool
id: split
label: split
cwlVersion: v1.0


inputs:
  bam_input:
    type: File
    doc: "The BAM file used as input, it must be sorted."
    format: "http://edamontology.org/format_2572"

outputs:
  bamstats_report:
    type: File
    format: "http://edamontology.org/format_3615"
    outputBinding:
      glob: test.aa
    secondaryFiles:
      - ^.ab
      - ^.ac
      - ^.ad
      - ^.ae
      - ^.af

baseCommand: ["split"]
arguments: ["--bytes=15000000", $(inputs.bam_input), "test."]