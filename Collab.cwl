class: CommandLineTool
description: "Markdown description text here"
id: "HelloWorld"
label: "HelloWorld Tool"
version: "1.0.0"

requirements:
  - class: DockerRequirement
    dockerPull: "quay.io/collaboratory/HelloWorld:1.0.0"
  - class: VMRequirements
    cores: 8
    ram_mb: 8092
    storage_gb: 512
    description: "these parameters are used to locate a VM with appropriate resources"

inputs:
  - id: "#ref_file_1"
    type: File
    inputBinding:
      position: 1
  - id: "#ref_file_2"
    type: File
    inputBinding:
      position: 2
  - id: "#hello-input"
    type: File
    inputBinding:
      position: 3

outputs:
  - id: "#hello-output"
    type: File
    outputBinding:
      glob: hello-output.txt

tests:
  -class: testCall
   command: "echo 'test1' > test1.txt"
  -class: testCall
   command: "echo 'test2' > test2.txt"

baseCommand: "cat"
stdout: output.txt
