#!/usr/bin/env cwl-runner
class: CommandLineTool
inputs:
  - id: "#infile"
    type: {type: array, items: File}
    inputBinding: {position: 1}
outputs:
  - id: "#outfile"
    type: File
    outputBinding: {glob: "out.txt"}
baseCommand: [wc, -l]
stdout: out.txt
