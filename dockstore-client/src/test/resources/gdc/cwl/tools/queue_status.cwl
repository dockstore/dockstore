#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/queue_status:4
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: input_signpost_id
    type: string
    inputBinding:
      prefix: --input_signpost_id

  - id: repo
    type: string
    inputBinding:
      prefix: --repo

  - id: repo_hash
    type: string
    inputBinding:
      prefix: --repo_hash

  - id: s3_url
    type: ["null", string]
    inputBinding:
      prefix: --s3_url

  - id: status
    type: string
    inputBinding:
      prefix: --status

  - id: table_name
    type: string
    inputBinding:
      prefix: --table_name

  - id: uuid
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: log
    type: File
    outputBinding:
      glob: $(inputs.uuid+".log")

  - id: sqlite
    type: File
    outputBinding:
      glob: $(inputs.uuid+".db")

baseCommand: [/usr/local/bin/queue_status]
