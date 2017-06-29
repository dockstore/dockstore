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
    type: Directory
    outputBinding:
      glob: test1

baseCommand: ["mkdir"]
arguments: ["-p", "test1/test2/test3/test4"]