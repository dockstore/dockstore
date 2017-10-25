#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/picard:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: BAIT_INTERVALS
    type: File
    inputBinding:
      prefix: BAIT_INTERVALS=
      separate: false

  - id: BAIT_SET_NAME
    type: ["null", string]
    inputBinding:
      prefix: BAIT_SET_NAME=
      separate: false

  - id: CLIP_OVERLAPPING_READS
    type: string
    default: "true"
    inputBinding:
      prefix: CLIP_OVERLAPPING_READS=
      separate: false

  - id: COVERAGE_CAP
    type: int
    default: 200
    inputBinding:
      prefix: COVERAGE_CAP=
      separate: false

  - id: INPUT
    type: File
    format: "edam:format_2572"
    inputBinding:
      prefix: INPUT=
      separate: false

  - id: METRIC_ACCUMULATION_LEVEL
    type: string
    default: ALL_READS
    inputBinding:
      prefix: METRIC_ACCUMULATION_LEVEL=
      separate: false

  - id: MINIMUM_BASE_QUALITY
    type: int
    default: 20
    inputBinding:
      prefix: MINIMUM_BASE_QUALITY=
      separate: false

  - id: MINIMUM_MAPPING_QUALITY
    type: int
    default: 20
    inputBinding:
      prefix: MINIMUM_MAPPING_QUALITY=
      separate: false

  - id: NEAR_DISTANCE
    type: int
    default: 250
    inputBinding:
      prefix: NEAR_DISTANCE=
      separate: false

  - id: OUTPUT
    type: string
    inputBinding:
      prefix: OUTPUT=
      separate: false

  - id: PER_BASE_COVERAGE
    type: ["null", File]
    inputBinding:
      prefix: PER_BASE_COVERAGE=
      separate: false

  - id: PER_TARGET_COVERAGE
    type: ["null", File]
    inputBinding:
      prefix: PER_TARGET_COVERAGE=
      separate: false

  - id: REFERENCE_SEQUENCE
    type: File
    format: "edam:format_1929"
    inputBinding:
      prefix: REFERENCE_SEQUENCE=
      separate: false
    secondaryFiles:
      - .fai

  - id: SAMPLE_SIZE
    type: int
    default: 10000
    inputBinding:
      prefix: SAMPLE_SIZE=
      separate: false

  - id: TARGET_INTERVALS
    type: File
    inputBinding:
      prefix: TARGET_INTERVALS=
      separate: false

  - id: VALIDATION_STRINGENCY
    default: STRICT
    type: string
    inputBinding:
      prefix: VALIDATION_STRINGENCY=
      separate: false

outputs:
  - id: METRIC_OUTPUT
    type: File
    outputBinding:
      glob: $(inputs.OUTPUT)

# arguments:
#   - valueFrom: $(inputs.INPUT.nameroot + ".metrics")
#     prefix: "OUTPUT="
#     separate: false

baseCommand: [java, -jar, /usr/local/bin/picard.jar, CollectHsMetrics]
