#!/usr/bin/env cwltool

cwlVersion: v1.0
class: CommandLineTool

requirements:
- class: DockerRequirement
  dockerPull: quay.io/ratschlab/workflow-experiment

baseCommand: [/scripts/line_counts.sh]

inputs:
  input_file:
    type: File
    inputBinding:
      position: 1
    label: "input file"

arguments:
  - valueFrom: $(runtime.outdir)
    position: 2

outputs:
  counts:
    type: File
    outputBinding:
      glob: "*.cnt"
    doc: "number of lines in file"
