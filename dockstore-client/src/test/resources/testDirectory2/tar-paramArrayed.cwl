cwlVersion: v1.0
class: CommandLineTool
baseCommand: [tar, xf]
inputs:
  tarfile:
    type: File
    inputBinding:
      position: 1
  extractfile:
    type: string
    inputBinding:
      position: 2
outputs:
  example_out:
    type:
        items:
        - File
        - 'null'
        type: array
    outputBinding:
      glob: $(inputs.extractfile)
