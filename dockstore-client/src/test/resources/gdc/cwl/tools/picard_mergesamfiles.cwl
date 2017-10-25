#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/picard:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: ASSUME_SORTED
    type: boolean
    default: false
    inputBinding:
      prefix: ASSUME_SORTED=
      separate: false

  - id: CREATE_INDEX
    type: string
    default: "true"
    inputBinding:
      prefix: CREATE_INDEX=
      separate: false

  - id: INPUT
    format: "edam:format_2572"
    type:
      type: array
      items: File

  - id: INTERVALS
    type: ["null", File]
    inputBinding:
      prefix: INTERVALS=
      separate: false

  - id: MERGE_SEQUENCE_DICTIONARIES
    type: string
    default: "false"
    inputBinding:
      prefix: MERGE_SEQUENCE_DICTIONARIES=
      separate: false

  - id: OUTPUT
    type: string
    inputBinding:
      prefix: OUTPUT=
      separate: false

  - id: SORT_ORDER
    type: string
    default: coordinate
    inputBinding:
      prefix: SORT_ORDER=
      separate: false

  - id: TMP_DIR
    type: string
    default: .
    inputBinding:
      prefix: TMP_DIR=
      separate: false

  - id: USE_THREADING
    type: string
    default: "true"
    inputBinding:
      prefix: USE_THREADING=
      separate: false

  - id: VALIDATION_STRINGENCY
    type: string
    default: STRICT
    inputBinding:
      prefix: VALIDATION_STRINGENCY=
      separate: false

outputs:
  - id: MERGED_OUTPUT
    type: File
    outputBinding:
      glob: $(inputs.OUTPUT)

arguments:
  - valueFrom: |
      ${
        if (inputs.INPUT.length == 0) {
          var cmd = ['/usr/bin/touch', inputs.OUTPUT];
          return cmd
        }
        else {
          var cmd = ["java", "-jar", "/usr/local/bin/picard.jar", "MergeSamFiles"];
          var use_input = [];
          for (var i = 0; i < inputs.INPUT.length; i++) {
            var filesize = inputs.INPUT[i].size;
            if (filesize > 0) {
              use_input.push("INPUT=" + inputs.INPUT[i].path);
            }
          }

          var run_cmd = cmd.concat(use_input);
          return run_cmd
        }

      }
baseCommand: []
