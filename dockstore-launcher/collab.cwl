#!/usr/bin/env cwl-runner

class: CommandLineTool
description: "Markdown description text here"
id: "HelloWorld"
label: "HelloWorld Tool"

dct:creator:
  "@id": "http://orcid.org/0000-0003-3566-7705"
  foaf:name: Peter Amstutz
  foaf:mbox: "mailto:peter.amstutz@curoverse.com"

requirements:
  - class: DockerRequirement
    dockerPull: "quay.io/collaboratory/workflow-helloworld:master"
  - { import: node-engine.cwl }

hints:
  - class: ResourceRequirement
    coresMin: 8
    ramMin: 8092
    outdirMin: 512000
    description: "these parameters are used to locate a VM with appropriate resources"

inputs:
  - id: "#ref_file_1"
    type: File
    description: "this describes a large reference file that does not change between runs"

  - id: "#ref_file_2"
    type: File
    description: "this describes a large reference file that does not change between runs"

  - id: "#hello_input"
    type: File
    description: "this describes an input file that should be provided before execution"

outputs:
  - id: "#hello_output"
    type: File
    outputBinding:
      glob: hello-output.txt
    description: "this describes an output file that should be saved after execution"

baseCommand: ["bash", "-c"]
arguments:
  - valueFrom:
      engine: node-engine.cwl
      script: |
        "cat " + $job.hello_input.path + " > hello-output.txt &&"
            + " ls " + $job.ref_file_1.path + " >> hello-output.txt && "
            + " head -20 " + $job.ref_file_2.path + " >> hello-output.txt"
