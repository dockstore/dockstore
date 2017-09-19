#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-3"

requirements:
  - class: ShellCommandRequirement
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam_path
    type:
      type: array
      items: File
      secondaryFiles: |
        ${
        return {"path": self.path.slice(0,-4)+".bai", "class": "File"};
        }

outputs:
  - id: bam_list
    type: File
    outputBinding:
      glob: "bam.list"

arguments:
  - valueFrom: ${
        var bam_list = "";
        for (var i = 0; i < inputs.bam_path.length; i ++) {
          bam_list += " echo " + inputs.bam_path[i].path.split('/').slice(-1)[0] + " >> bam.list &&"
        }
        return bam_list.slice(0,-2)
        }
    position: 1
    shellQuote: false

baseCommand: []
