#!/usr/bin/env cwl-runner

cwlVersion: draft-3

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cocleaning-tool:3.7
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: consensusDeterminationModel
    type: string
    default: USE_READS
    inputBinding:
      prefix: --consensusDeterminationModel

  - id: entropyThreshold
    type: double
    default: 0.15
    inputBinding:
      prefix: --entropyThreshold

  - id: input_file
    #format: "edam:format_2572"
    type:
      type: array
      items: File
      inputBinding:
        prefix: --input_file
      secondaryFiles:
        - ^.bai

  - id: knownAlleles
    #format: "edam:format_3016"
    type: File
    inputBinding:
      prefix: --knownAlleles
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

  - id: LODThresholdForCleaning
    type: double
    default: 5.0
    inputBinding:
      prefix: --LODThresholdForCleaning

  - id: maxConsensuses
    type: int
    default: 30
    inputBinding:
      prefix: --maxConsensuses

  - id: maxIsizeForMovement
    type: int
    default: 3000
    inputBinding:
      prefix: --maxIsizeForMovement

  - id: maxPositionalMoveAllowed
    type: int
    default: 200
    inputBinding:
      prefix: --maxPositionalMoveAllowed

  - id: maxReadsForConsensuses
    type: int
    default: 120
    inputBinding:
      prefix: --maxReadsForConsensuses

  - id: maxReadsForRealignment
    type: int
    default: 20000
    inputBinding:
      prefix: --maxReadsForRealignment

  - id: maxReadsInMemory
    type: int
    default: 150000
    inputBinding:
      prefix: --maxReadsInMemory

  - id: noOriginalAlignmentTags
    type: boolean
    default: true
    inputBinding:
      prefix: --noOriginalAlignmentTags

  - id: nWayOut
    type: string
    default: ".bam"
    inputBinding:
      prefix: --nWayOut

  - id: reference_sequence
    #format: "edam:format_1929"
    type: File
    inputBinding:
      prefix: --reference_sequence
    secondaryFiles:
      - ^.dict
      - .fai

  - id: targetIntervals
    type: File
    inputBinding:
      prefix: --targetIntervals

outputs:
  - id: output_bam
    #format: "edam:format_2572"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*.bam"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }
    secondaryFiles:
      - ^.bai

  - id: output_log
    type: File
    outputBinding:
      glob: $(inputs.log_to_file)

baseCommand: [java, -jar, /usr/local/bin/GenomeAnalysisTK.jar, -T, IndelRealigner]
