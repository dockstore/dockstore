#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InlineJavascriptRequirement

class: ExpressionTool

inputs:
  - id: INPUT
    type:
      type: array
      items: File

outputs:
  - id: OUTPUT
    type:
      type: array
      items: File

expression: |
  ${
    var output = inputs.INPUT.sort(function(a,b) { return a.basename > b.basename ? 1 : (a.basename < b.basename ? -1 : 0) });
    return {'OUTPUT': output}
  }
