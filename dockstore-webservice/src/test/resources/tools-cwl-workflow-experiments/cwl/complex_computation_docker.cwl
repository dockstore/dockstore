#!/usr/bin/env cwltool

cwlVersion: v1.0
class: CommandLineTool

baseCommand: [python3, /scripts/complex_computation.py]

requirements:
- class: DockerRequirement
  dockerPull: quay.io/ratschlab/workflow-experiment

inputs:
  input_file:
    type: File
    inputBinding:
      position: 1
    label: "input file"

  exponent:
    type: int
    inputBinding:
      position: 2
    label: "exponent to regulate runtime"

arguments:
  - valueFrom: $(runtime.outdir)/$(inputs.input_file.nameroot).res
    position: 3

outputs:
  counts:
    type: File
    outputBinding:
      glob: "*.res"
    doc: "number of lines in file"
