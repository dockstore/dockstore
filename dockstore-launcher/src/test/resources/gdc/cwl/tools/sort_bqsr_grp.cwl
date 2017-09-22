#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: ubuntu:xenial-20160923.1
  - class: InlineJavascriptRequirement
  - class: ShellCommandRequirement

class: CommandLineTool

inputs:
  - id: input_grp
    type:
      type: array
      items: File

outputs:
  - id: output_grp
    type:
      type: array
      items: File
    outputBinding:
      outputEval: |
      glob: "*_bqsr.grp"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

arguments:
  - valueFrom: |
      ${
         var cmd = "cp ";
         for (var i = 0; i < inputs.input_grp.length; i++) {
          cmd += inputs.input_grp[i].path + " ";
         }
         cmd += "."
         return cmd
      }
    shellQuote: false

baseCommand: []
