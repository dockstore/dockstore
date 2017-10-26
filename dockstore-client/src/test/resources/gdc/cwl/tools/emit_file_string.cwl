#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InlineJavascriptRequirement

class: ExpressionTool

inputs:
  - id: input
    type: File
    inputBinding:
      loadContents: true
      valueFrom: null

outputs:
  - id: output
    type: string

expression: |
  ${
    var output = inputs.input.contents;
    return {'output': output}
  }
