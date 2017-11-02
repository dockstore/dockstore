#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/sqlite_to_postgres:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: ini_section
    type: string
    inputBinding:
      prefix: --ini_section

  - id: postgres_creds_path
    type: File
    inputBinding:
      prefix: --postgres_creds_path

  - id: source_sqlite_path
    type: File
    inputBinding:
      prefix: --source_sqlite_path

  - id: uuid
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: log
    type: File
    outputBinding:
      glob: $(inputs.uuid + ".log")
          
baseCommand: [sqlite_to_postgres]
