#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/queue_coclean_status
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam_normal_signpost_id
    type: string
    inputBinding:
      prefix: --bam_normal_signpost_id

  - id: bam_tumor_signpost_id
    type: string
    inputBinding:
      prefix: --bam_tumor_signpost_id

  - id: bam_normal_uuid
    type: ["null", string]
    inputBinding:
      prefix: --bam_normal_uuid

  - id: bam_tumor_uuid
    type: ["null", string]
    inputBinding:
      prefix: --bam_tumor_uuid

  - id: hostname
    type: string
    inputBinding:
      prefix: --hostname

  - id: host_ipaddress
    type: string
    inputBinding:
      prefix: --host_ipaddress

  - id: host_mac
    type: string
    inputBinding:
      prefix: --host_mac

  - id: known_indel_signpost_id
    type: string
    inputBinding:
      prefix: --known_indel_signpost_id

  - id: known_snp_signpost_id
    type: string
    inputBinding:
      prefix: --known_snp_signpost_id

  - id: num_threads
    type: int
    inputBinding:
      prefix: --num_threads

  - id: reference_fa_signpost_id
    type: string
    inputBinding:
      prefix: --reference_fa_signpost_id

  - id: repo
    type: string
    inputBinding:
      prefix: --repo

  - id: repo_hash
    type: string
    inputBinding:
      prefix: --repo_hash

  - id: run_uuid
    type: string
    inputBinding:
      prefix: --run_uuid

  - id: s3_bam_normal_url
    type: ["null", string]
    inputBinding:
      prefix: --s3_bam_normal_url

  - id: s3_bam_tumor_url
    type: ["null", string]
    inputBinding:
      prefix: --s3_bam_tumor_url

  - id: status
    type: string
    inputBinding:
      prefix: --status

  - id: table_name
    type: string
    inputBinding:
      prefix: --table_name

outputs:
  - id: log
    type: File
    outputBinding:
      glob: $(inputs.run_uuid+".log")

  - id: sqlite
    type: File
    outputBinding:
      glob: $(inputs.run_uuid+".db")

baseCommand: [/usr/local/bin/queue_coclean_status]
