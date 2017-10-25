#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

description: |
  Usage:  cwl-runner <this-file-path> --source_sqlite <source-sqlite-path> --uuid <uuid-string>
  Options:
    --source_sqlite  source sqlite to insert in postgres
    --uuid           UUID for log file and sqlite db file

requirements:
  - $import: envvar-global.cwl
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/sqlite_to_postgres

class: CommandLineTool

inputs:
  - id: "#source_sqlite_path"
    type: File
    inputBinding:
      prefix: --source_sqlite_path

  - id: "#uuid"
    type: string
    inputBinding:
      prefix: --uuid

  - id: "#postgres_creds_s3url"
    type: string
    inputBinding:
      prefix: --postgres_creds_s3url

  - id: "#ini_section"
    type: string
    inputBinding:
      prefix: --ini_section

  - id: "#s3cfg_path"
    type: File
    inputBinding:
      prefix: --s3cfg_path

outputs:
  - id: "#log"
    type: File
    description: "python log file"
    outputBinding:
      glob: $(inputs.uuid + "_sqlite_to_postgres.log")
          
baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.4/site-packages/sqlite_to_postgres/main.py"]
