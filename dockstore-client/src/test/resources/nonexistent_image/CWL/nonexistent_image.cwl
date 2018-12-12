cwlVersion: v1.0
class: CommandLineTool
requirements:
- class: DockerRequirement
  dockerPull: dockstore.org/bashwithbinbash:0118999881999119725...3
- class: InlineJavascriptRequirement
inputs:
  message:
    type: string
    inputBinding:
      position: 1
outputs: []

baseCommand: echo
