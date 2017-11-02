#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cocleaning-tool:3.7
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: binary_tag_name
    type: ["null", string]
    inputBinding:
      prefix: --binary_tag_name

  - id: bqsrBAQGapOpenPenalty
    type: double
    default: 40
    inputBinding:
      prefix: --bqsrBAQGapOpenPenalty

  - id: covariate
    type: ["null", string]
    inputBinding:
      prefix: --covariate

  - id: deletions_default_quality
    type: int
    default: 45
    inputBinding:
      prefix: --deletions_default_quality

  - id: indels_context_size
    type: int
    default: 3
    inputBinding:
      prefix: --indels_context_size

  - id: input_file
    #format: "edam:format_2572"
    type: File
    inputBinding:
      prefix: --input_file
    secondaryFiles:
      - ^.bai

  - id: insertions_default_quality
    type: int
    default: 45
    inputBinding:
      prefix: --insertions_default_quality

  - id: knownSites
    #format: "edam:format_3016"
    type: File
    inputBinding:
      prefix: --knownSites
    secondaryFiles:
      - .tbi

  - id: list
    type: boolean
    default: false
    inputBinding:
      prefix: --list

  - id: log_to_file
    type: string
    inputBinding:
      prefix: --log_to_file

  - id: --logging_level
    default: INFO
    type: string
    inputBinding:
      prefix: --logging_level

  - id: low_quality_tail
    type: int
    default: 2
    inputBinding:
      prefix: --low_quality_tail

  - id: lowMemoryMode
    type: boolean
    default: false
    inputBinding:
      prefix: --lowMemoryMode

  - id: maximum_cycle_value
    type: int
    default: 500
    inputBinding:
      prefix: --maximum_cycle_value

  - id: mismatches_context_size
    type: int
    default: 2
    inputBinding:
      prefix: --mismatches_context_size

  - id: mismatches_default_quality
    type: int
    default: -1
    inputBinding:
      prefix: --mismatches_default_quality

  - id: no_standard_covs
    type: boolean
    default: false
    inputBinding:
      prefix: --no_standard_covs

  - id: num_cpu_threads_per_data_thread
    type: int
    default: 1
    inputBinding:
      prefix: --num_cpu_threads_per_data_thread

  - id: --quantizing_levels
    type: int
    default: 16
    inputBinding:
      prefix: --quantizing_levels

  - id: run_without_dbsnp_potentially_ruining_quality
    type: boolean
    default: false
    inputBinding:
      prefix: --run_without_dbsnp_potentially_ruining_quality

  - id: sort_by_all_columns
    type: boolean
    default: false
    inputBinding:
      prefix: --sort_by_all_columns

  - id: reference_sequence
    #format: "edam:format_1929"
    type: File
    inputBinding:
      prefix: --reference_sequence
    secondaryFiles:
      - .fai
      - ^.dict

outputs:
  - id: output_grp
    type: File
    outputBinding:
      glob: $(inputs.input_file.nameroot + "_bqsr.grp")

  - id: output_log
    type: File
    outputBinding:
      glob: $(inputs.log_to_file)

arguments:
  - valueFrom: $(inputs.input_file.nameroot + "_bqsr.grp")
    prefix: --out
    separate: true

baseCommand: [java, -jar, /usr/local/bin/GenomeAnalysisTK.jar, -T, BaseRecalibrator]
