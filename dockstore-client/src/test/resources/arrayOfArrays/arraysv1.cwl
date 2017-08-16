#!/usr/bin/env cwl-runner

class: CommandLineTool
description: "Markdown description text here"
id: "HelloWorld"
label: "HelloWorld Tool"

cwlVersion: v1.0

dct:creator:
  "@id": "http://orcid.org/0000-0003-3566-7705"
  foaf:name: Peter Amstutz
  foaf:mbox: "mailto:peter.amstutz@curoverse.com"

requirements:
  - class: DockerRequirement
    dockerPull: "quay.io/collaboratory/dockstore-tool-linux-sort"
  - class: InlineJavascriptRequirement

hints:
  - class: ResourceRequirement
    coresMin: 8
    ramMin: 8092
    outdirMin: 512000
    description: "these parameters are used to locate a VM with appropriate resources"

inputs:
  ref_file_1:
    type: File
    description: "this describes a large reference file that does not change between runs"

  ref_file_2:
    type: File
    description: "this describes a large reference file that does not change between runs"

  hello_input:
    type: File
    description: "this describes an input file that should be provided before execution"

  arrayed_input:
    type:
      items:
        items: File
        type: array
      type: array
    description: "this demonstrates a workflow that takes an array of inputs, at least 2"

outputs:
  hello_output:
    type: File
    outputBinding:
      glob: hello-output.txt
    description: "this describes an output file that should be saved after execution"

  wc_output:
    type:
      type: array
      items: File
    outputBinding:
      glob: wc-output*.txt
    description: "this describes an output file that should be saved after execution"


baseCommand: ["bash", "-c"]
arguments:
  - valueFrom:
        $("cat " + inputs.hello_input.path + " > hello-output.txt &&"
            + " ls " + inputs.ref_file_1.path + " >> hello-output.txt && "
            + " head -20 " + inputs.ref_file_2.path + " >> hello-output.txt && "
            + " wc -l " + inputs.arrayed_input[0][0].path + " >> wc-output0.txt &&"
            + " wc -l " + inputs.arrayed_input[0][1].path + " >> wc-output1.txt")
