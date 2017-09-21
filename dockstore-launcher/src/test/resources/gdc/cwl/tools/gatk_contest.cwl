#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cocleaning-tool:3.7
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: base_report
    type: ["null", string]
    inputBinding:
      prefix: --base_report

  - id: beta_threshold
    type: double
    default: 0.95
    inputBinding:
      prefix: --beta_threshold

  - id: genotype_mode
    type: string
    default: HARD_THRESHOLD
    inputBinding:
      prefix: --genotype_mode

  - id: genotypes
    type: ["null", File]
    #format: "edam:format_3016"
    inputBinding:
      prefix: --genotypes

  - id: INPUT
    type: File
    #format: "edam:format_2572"
    inputBinding:
      prefix: -I
    secondaryFiles:
      - ^.bai

  - id: lane_level_contamination
    type: string
    default: META
    inputBinding:
      prefix: --lane_level_contamination

  - id: out
    type: string
    inputBinding:
      prefix: --out

  - id: popfile
    type: File
    #format: "edam:format_3016"
    inputBinding:
      prefix: --popfile
    secondaryFiles:
      - .tbi

  - id: REFERENCE_SEQUENCE
    type: File
    #format: "edam:format_1929"
    inputBinding:
      prefix: -R
    secondaryFiles:
      - .fai
      - ^.dict

outputs:
  - id: OUTPUT
    type: File
    outputBinding:
      glob: $(inputs.out)

baseCommand: [java, -jar, /usr/local/bin/GenomeAnalysisTK.jar, -T, ContEst]
