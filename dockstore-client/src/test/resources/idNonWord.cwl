cwlVersion: v1.0
class: CommandLineTool
baseCommand: echo
inputs:
  message:
    type: string
    inputBinding:
      position: 1
outputs:
    - id: "#example_out"
      type: File
      outputBinding:
        glob: hello.txt