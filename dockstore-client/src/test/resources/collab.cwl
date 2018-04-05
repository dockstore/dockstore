#!/usr/bin/env cwl-runner

class: CommandLineTool
id: HelloWorld
label: HelloWorld Tool
cwlVersion: v1.0

requirements:
- class: DockerRequirement
  dockerPull: ubuntu:trusty
- class: InlineJavascriptRequirement

hints:
- class: ResourceRequirement
  coresMin: 8
  ramMin: 8092
  outdirMin: 512000
inputs:
  hello_input:
    type: File
    doc: this describes an input file that should be provided before execution
  ref_file_2:
    type: File
    doc: this describes a large reference file that does not change between runs
  ref_file_1:
    type: File
    doc: this describes a large reference file that does not change between runs
outputs:
  hello_output:
    type: File
    outputBinding:
      glob: hello-output.txt
    doc: this describes an output file that should be saved after execution
baseCommand: [bash, -c]
arguments:
- valueFrom: $("cat " + inputs.hello_input.path + " > hello-output.txt &&" + " ls
    " + inputs.ref_file_1.path + " >> hello-output.txt && " + " head -20 " + inputs.ref_file_2.path
    + " >> hello-output.txt")
doc: Markdown description text here

