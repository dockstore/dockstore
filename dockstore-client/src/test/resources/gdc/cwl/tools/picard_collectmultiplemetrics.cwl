#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/picard:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: DB_SNP
    type: File
    format: "edam:format_3016"
    inputBinding:
      prefix: DB_SNP=
      separate: false

  - id: INPUT
    type: File
    format: "edam:format_2572"
    inputBinding:
      prefix: INPUT=
      separate: false

  - id: METRIC_ACCUMULATION_LEVEL=
    type: string
    default: ALL_READS
    inputBinding:
      prefix: METRIC_ACCUMULATION_LEVEL=
      separate: false

  - id: REFERENCE_SEQUENCE
    type: File
    format: "edam:format_1929"
    inputBinding:
      prefix: REFERENCE_SEQUENCE=
      separate: false

  - id: TMP_DIR
    type: string
    default: .
    inputBinding:
      prefix: TMP_DIR=
      separate: false

  - id: VALIDATION_STRINGENCY
    default: STRICT
    type: string
    inputBinding:
      prefix: VALIDATION_STRINGENCY=
      separate: false

outputs:
  - id: alignment_summary_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".alignment_summary_metrics")

  - id: bait_bias_detail_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".bait_bias_detail_metrics")

  - id: bait_bias_summary_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".bait_bias_summary_metrics")

  - id: base_distribution_by_cycle_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".base_distribution_by_cycle_metrics")

  - id: base_distribution_by_cycle_pdf
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".base_distribution_by_cycle.pdf")

  - id: gc_bias_detail_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".gc_bias.detail_metrics")

  - id: gc_bias_pdf
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".gc_bias.pdf")

  - id: gc_bias_summary_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".gc_bias.summary_metrics")

  - id: insert_size_histogram_pdf
    type: ["null", File]
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".insert_size_histogram.pdf")

  - id: insert_size_metrics
    type: ["null", File]
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".insert_size_metrics")

  - id: pre_adapter_detail_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".pre_adapter_detail_metrics")

  - id: pre_adapter_summary_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".pre_adapter_summary_metrics")

  - id: quality_by_cycle_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".quality_by_cycle_metrics")

  - id: quality_by_cycle_pdf
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".quality_by_cycle.pdf")

  - id: quality_distribution_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".quality_distribution_metrics")

  - id: quality_distribution_pdf
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".quality_distribution.pdf")

  - id: quality_yield_metrics
    type: File
    outputBinding:
      glob: $(inputs.INPUT.nameroot + ".quality_yield_metrics")

arguments:
  - valueFrom: "PROGRAM=CollectAlignmentSummaryMetrics"
  - valueFrom: "PROGRAM=CollectBaseDistributionByCycle"
  - valueFrom: "PROGRAM=CollectGcBiasMetrics"
  - valueFrom: "PROGRAM=CollectInsertSizeMetrics"
  - valueFrom: "PROGRAM=CollectQualityYieldMetrics"
  - valueFrom: "PROGRAM=CollectSequencingArtifactMetrics"
  - valueFrom: "PROGRAM=MeanQualityByCycle"
  - valueFrom: "PROGRAM=QualityScoreDistribution"

  - valueFrom: $(inputs.INPUT.nameroot)
    prefix: OUTPUT=
    separate: false

baseCommand: [java, -jar, /usr/local/bin/picard.jar, CollectMultipleMetrics]
