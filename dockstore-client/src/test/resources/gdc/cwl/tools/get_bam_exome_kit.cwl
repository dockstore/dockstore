#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/gdc_bam_library_exomekit
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: bam
    type: string

  - id: library
    type: File
    inputBinding:
      loadContents: true
      valueFrom: null

outputs:
  - id: exome_kit
    type:
      type: array
      item: File
    outputBinding:
      glob: $(inputs.library.basename + ".kit")

stdout: $(inputs.library.basename + ".kit")

arguments:
  - valueFrom: |
      ${
      var cmd = "exec(\"import json\\nwith open('/usr/local/share/bam_libraryname_capturekey.json') as data_file: data = json.load(data_file)\\nprint(data['" + inputs.bam + "']['" + inputs.library.contents.slice(0,-1) + "'])\")";
      return cmd
      }

baseCommand: [python3, -c]
