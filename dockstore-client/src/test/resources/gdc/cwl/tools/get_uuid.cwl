#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: python:3.6.0-slim

class: CommandLineTool

inputs:
  []

outputs:
  - id: uuid
    type: File
    outputBinding:
      glob: uuid

stdout: uuid

baseCommand: [bash, -c, python3 -c 'import uuid; import sys; sys.stdout.write(str(uuid.uuid4()));']
