#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/table_string_sqlite_cell:1

class: CommandLineTool

inputs:
  - id: cell_value
    type: string
    inputBinding:
      prefix: --cell_value

  - id: table_name
    type: string
    inputBinding:
      prefix: --table_name

outputs:
  - id: sqlite
    type: File
    outputBinding:
      glob: "output.db"

baseCommand: [/usr/local/bin/table_string_sqlite_cell]
