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
      glob: test.orig
    secondaryFiles:
      - .abextra
      - .acextra
      - .adextra
      - .aeextra
      - .afextra
      - .funky.extra.stuff
      - .groovyextrastuff

baseCommand: ["bash"]
arguments: ["-c", "cp $(inputs.bam_input.path) test.orig; dmesg &> test.orig.funky.extra.stuff ; dmesg &> test.orig.groovyextrastuff ; split --bytes=15000000 --additional-suffix=extra $(inputs.bam_input.path) test.orig."]