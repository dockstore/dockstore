#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: CommandLineTool

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/curl:1
  - class: InlineJavascriptRequirement

inputs:
  - id: signpost_id
    type: string

  - id: base_url
    type: string

stdout: output.json

outputs:
  - id: output
    type: File
    outputBinding:
      glob: output.json

arguments:
  - valueFrom: |
      ${
         function endsWith(str, suffix) {
           return str.indexOf(suffix, str.length - suffix.length) !== -1;
         }
         if ( endsWith(inputs.base_url, '/') ) {
           return inputs.base_url + inputs.signpost_id;
         }
         else {
           return inputs.base_url + '/' + inputs.signpost_id;
         }
      }
    position: 0

baseCommand: [curl]
