class: CommandLineTool
cwlVersion: v1.0
requirements:
  - class: ShellCommandRequirement
  - class: DockerRequirement
    dockerPull: ubuntu
inputs:
  indir:
    type: Directory
    default: funky_dir
    inputBinding:
      prefix: cd
      position: -1
outputs:
  outlist:
    type: File
    outputBinding:
      glob: output.txt

arguments: [
  {shellQuote: false, valueFrom: "&&"},
  "find", ".",
  {shellQuote: false, valueFrom: "|"},
  "sort"]
stdout: output.txt

baseCommand: []