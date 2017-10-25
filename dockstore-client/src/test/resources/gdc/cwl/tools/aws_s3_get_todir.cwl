#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: CommandLineTool
requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/awscli:1
  - class: EnvVarRequirement
    envDef:
      - envName: "AWS_CONFIG_FILE"
        envValue: $(inputs.aws_config_file.path)
      - envName: "AWS_SHARED_CREDENTIALS_FILE"
        envValue: $(inputs.aws_shared_credentials_file.path)
  - class: InlineJavascriptRequirement
  - class: ShellCommandRequirement

inputs:
  - id: aws_config_file
    type: File

  - id: aws_shared_credentials_file
    type: File
  
  - id: endpoint_url
    type: string
    inputBinding:
      prefix: --endpoint-url
      position: 10

  - id: input_dir
    type: Directory
    
  - id: s3cfg_section
    type: string
    inputBinding:
      prefix: --profile
      position: 10
    
  - id: s3uri
    type: string
    inputBinding:
      position: 98

outputs:
  - id: output
    type: File
    outputBinding:
      glob: $(inputs.s3uri.split('/').slice(-1)[0])

  - id: bin_time
    type: File
    outputBinding:
      glob: "bin.time"

arguments:
  - valueFrom: "bin.time"
    position: 0

  - valueFrom: aws
    position: 1

  - valueFrom: s3
    position: 2

  - valueFrom: cp
    position: 3

  - valueFrom: $(inputs.input_dir)
    position: 99

baseCommand: [/usr/bin/time, -v, -o]
