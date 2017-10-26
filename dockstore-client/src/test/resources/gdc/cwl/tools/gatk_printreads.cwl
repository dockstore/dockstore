#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cocleaning-tool:3.7
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: BQSR
    type: ["null", File]
    inputBinding:
      prefix: --BQSR

  - id: input_file
    #format: "edam:format_2572"
    type: File
    inputBinding:
      prefix: --input_file
    secondaryFiles:
      - ^.bai

  - id: log_to_file
    type: string
    inputBinding:
      prefix: --log_to_file

  - id: --logging_level
    default: INFO
    type: string
    inputBinding:
      prefix: --logging_level

  - id: num_cpu_threads_per_data_thread
    type: int
    default: 1
    inputBinding:
      prefix: --num_cpu_threads_per_data_thread

  - id: number
    type: int
    default: -1
    inputBinding:
      prefix: --number
    
  - id: platform
    type: ["null", string]
    inputBinding:
      prefix: --platform

  - id: readGroup
    type: ["null", string]
    inputBinding:
      prefix: --readGroup

  - id: reference_sequence
    #format: "edam:format_1929"
    type: File
    inputBinding:
      prefix: --reference_sequence
    secondaryFiles:
      - .fai
      - ^.dict

  - id: sample_file
    type: ["null", string]
    inputBinding:
      prefix: --sample_file

  - id: sample_name
    type: ["null", string]
    inputBinding:
      prefix: --sample_name

  - id: simplify
    type: boolean
    default: false
    inputBinding:
      prefix: --simplify

outputs:
  - id: output_bam
    #format: "edam:format_2572"
    type: File
    outputBinding:
      glob: $(inputs.input_file.basename)
    secondaryFiles:
      - ^.bai

  - id: output_log
    type: File
    outputBinding:
      glob: $(inputs.log_to_file)

arguments:
  - valueFrom: $(inputs.input_file.basename)
    prefix: --out
    separate: true

baseCommand: [java, -jar, /usr/local/bin/GenomeAnalysisTK.jar, -T, PrintReads]
