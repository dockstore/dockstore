#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: CommandLineTool
requirements:
  - class: DockerRequirement
    dockerPull: ubuntu:xenial-20161010

inputs:
  - id: load1
    type: ["null", File]

  - id: load2
    type: ["null", File]

  - id: load3
    type: ["null", File]

  - id: load4
    type: ["null", File]

  - id: load5
    type: ["null", File]

  - id: load6
    type: ["null", File]

  - id: load7
    type: ["null", File]

outputs:
  - id: token
    type: File
    outputBinding:
      glob: "token"
    
baseCommand: [touch, token]
