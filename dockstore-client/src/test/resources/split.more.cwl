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
      glob: test.txt.aa
    secondaryFiles:
      - ^.ab
      - ^.ac
      - ^.ad
      - ^.ae
      - ^^.extra
# does not seem to work in cwltool 1.0.20170828135420      - ^^^.superfluous

baseCommand: ["bash"]
arguments: ["-c", "cp $(inputs.bam_input.path) test.extra; cp $(inputs.bam_input.path) test.superfluous ; split --bytes=15000000 $(inputs.bam_input.path) test.txt."]