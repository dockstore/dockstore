#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/dnaseq_validation
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: input_sqlite_metrics
    type: File
    inputBinding:
      prefix: --input_sqlite_metrics

  - id: input_json_expected_metrics
    type: File
    inputBinding:
      prefix: --input_json_expected_metrics

outputs:
  - id: log
    type: File
    outputBinding:
      glob: $("log.txt")

  - id: results
    type: File
    outputBinding:
      glob: $("results.json")

baseCommand: [/usr/local/bin/dnaseq_validation]
