#!/usr/bin/env cwl-runner

cwlVersion: draft-3

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cocleaning-tool:3.7
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: input_file
    #format: "edam:format_2572"
    type:
      type: array
      items: File
      inputBinding:
        prefix: --input_file
      secondaryFiles:
        - ^.bai

  - id: known
    #format: "edam:format_3016"
    type: File
    inputBinding:
      prefix: --known
    secondaryFiles:
      - .tbi

  - id: log_to_file
    type: string
    inputBinding:
      prefix: --log_to_file

  - id: --logging_level
    default: INFO
    type: string
    inputBinding:
      prefix: --logging_level

  - id: maxIntervalSize
    type: int
    default: 500
    inputBinding:
      prefix: --maxIntervalSize

  - id: minReadsAtLocus
    type: int
    default: 4
    inputBinding:
      prefix: --minReadsAtLocus

  - id: mismatchFraction
    type: double
    default: 0.0
    inputBinding:
      prefix: --mismatchFraction

  - id: out
    type: string
    inputBinding:
      prefix: --out

  - id: num_threads
    type: int
    default: 1
    inputBinding:
      prefix: --num_threads

  - id: windowSize
    type: int
    default: 10
    inputBinding:
      prefix: --windowSize

  - id: reference_sequence
    #format: "edam:format_1929"
    type: File
    inputBinding:
      prefix: --reference_sequence
    secondaryFiles:
      - ^.dict
      - .fai

outputs:
  - id: output_intervals
    type: File
    outputBinding:
      glob: $(inputs.out)

  - id: output_log
    type: File
    outputBinding:
      glob: $(inputs.log_to_file)

baseCommand: [java, -jar, /usr/local/bin/GenomeAnalysisTK.jar, -T, RealignerTargetCreator]
