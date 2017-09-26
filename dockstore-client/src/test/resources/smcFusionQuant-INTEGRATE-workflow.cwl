#!/usr/bin/env cwl-runner
#

class: Workflow
cwlVersion: v1.0

doc: "INTEGRATE workflow: untar, tophat align, samtools index, Integrate fusion"

requirements:
  - class: MultipleInputFeatureRequirement

inputs:

  index: File

  TUMOR_FASTQ_1: File

  TUMOR_FASTQ_2: File

outputs:

  OUTPUT:
    type: File
    outputSource: integrate/integrate_fusions

steps:

  tar:
    run: general_tools/tar.cwl
    in:
      input: index
    out: [output]

  tophat:
    run: tophat/cwl/tophat.cwl
    in:
      p: { default: 15 }
      r: { default: 0 }
      bowtie1: { default: true }
      no-novel-junc: { default: true }
      o: { default: tophat_out }
      bowtie_index: tar/output
      fastq1: TUMOR_FASTQ_1
      fastq2: TUMOR_FASTQ_2
    out: [tophatOut_accepted_hits,tophatOut_unmapped]

  samtools_accepted:
    run: integrate/cwl/samtools_index.cwl
    in:
      bam: tophat/tophatOut_accepted_hits
    out: [out_index]

  samtools_unmapped:
    run: integrate/cwl/samtools_index.cwl
    in:
      bam: tophat/tophatOut_unmapped
    out: [out_index]

  integrate:
    run: integrate/cwl/integrate.cwl
    in:
      accepted: samtools_accepted/out_index
      unmapped: samtools_unmapped/out_index
      o: { default: "fusions.bedpe" }
      index: tar/output
    out: [integrate_fusions]
