#!/usr/bin/env cwl-runner
class: CommandLineTool
inputs:
  - id: "#pattern"
    type: string
    inputBinding: {position: 0}
  - id: "#infile"
    type: File
    inputBinding: {position: 1}
outputs:
  - id: "#outfile"
    type: File
    outputBinding: {glob: "out.txt"}
baseCommand: grep
stdout: out.txt
