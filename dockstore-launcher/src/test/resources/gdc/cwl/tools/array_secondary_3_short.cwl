#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

requirements:
  - class: ShellCommandRequirement
  - class: InlineJavascriptRequirement
  - class: DockerRequirement
    dockerPull: alpine

class: CommandLineTool

inputs:
  - id: bam_path
    type:
      type: array
      items: File
      secondaryFiles:
        - ^.bai

outputs:
  - id: bai_list
    type: File
    outputBinding:
      glob: "bai.list"

arguments:
  - valueFrom: ${
        var bai_list = "";
        for (var i = 0; i < inputs.bam_path.length; i ++) {
          bai_list += " cat " + inputs.bam_path[i].path.slice(0,-4)+".bai" + " >> bai.list && "
        }
        return bai_list.slice(0,-3)
        }
    position: 1
    shellQuote: false

baseCommand: []
