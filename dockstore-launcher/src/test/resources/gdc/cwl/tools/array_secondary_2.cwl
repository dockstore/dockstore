#!/usr/bin/env cwl-runner

cwlVersion: "cwl:draft-2"

requirements:
  - import: node-engine.cwl
  - import: envvar-global.cwl

class: CommandLineTool

inputs:
  - id: "#bam_path"
    type:
      type: array
      items: File
      inputBinding:
        secondaryFiles:
          - engine: node-engine.cwl
            script: |
              {
              return {"path": $self.path.slice(0,-4)+".bai", "class": "File"};
              }

outputs:
  - id: "#afile"
    type: File
    outputBinding:
      glob: "test.txt"

#return $inputs.bam_path.path.split('/').slice(-1)[0] need to build an array, not a file

# arguments:
#   - valueFrom:
#       engine: node-engine.cwl
#       script: |
#         {
#         var bam_list = "";
#         for (var i = 0; i < inputs.bam_path.length; i ++) {
#           bam_list += " echo " + inputs.bam_path[i].path.split('/').slice(-1)[0] + " >> bam.list &&"
#         }
#         return bam_list.slice(0,-2)
#         }
#     position: 1
#     shellQuote: false

baseCommand: [touch, test.txt]
