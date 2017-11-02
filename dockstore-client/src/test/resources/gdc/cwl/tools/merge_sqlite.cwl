#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/merge_sqlite:3
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: source_sqlite
    format: "edam:format_3621"
    type:
      type: array
      items: File
      inputBinding:
        prefix: "--source_sqlite"

  - id: uuid
    type: string
    inputBinding:
      prefix: "--uuid"

outputs:
  - id: destination_sqlite
    format: "edam:format_3621"
    type: File
    outputBinding:
      glob: $(inputs.uuid + ".db")

  - id: log
    type: File
    outputBinding:
      glob: $(inputs.uuid + ".log")

baseCommand: [/usr/local/bin/merge_sqlite]
