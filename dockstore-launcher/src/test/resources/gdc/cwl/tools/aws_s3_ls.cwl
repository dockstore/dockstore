#!/usr/bin/env cwl-runner
cwlVersion: "cwl:draft-3"

class: CommandLineTool
requirements:
  - class: ShellCommandRequirement
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/awscli:1

inputs:
  - id: endpoint-url
    type: ["null", string]
    inputBinding:
      prefix: --endpoint-url
      position: 10
      
  - id: output
    type: string

  - id: profile
    type: ["null", string]
    inputBinding:
      prefix: --profile
      position: 10
    
  - id: recursive
    type: ["null", boolean]
    inputBinding:
      prefix: --recursive
      position: 10

  - id: S3Uri
    type: string
    inputBinding:
      position: 99

outputs:
  - id: "#list"
    type: File
    outputBinding:
      glob: $(inputs.output)

stdout: $(inputs.output)

arguments:
  - valueFrom: "bin.time"
    position: 0

  - valueFrom: aws
    position: 1

  - valueFrom: s3
    position: 2

  - valueFrom: ls
    position: 3

baseCommand: [/usr/bin/time, -v, -o]
