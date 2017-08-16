#!/usr/bin/env cwl-runner
#

class: Workflow
cwlVersion: v1.0

doc: "Mutect workflow"

requirements:
  - class: MultipleInputFeatureRequirement

inputs:

  INDEX: File

  TUMOR_BAM: File

  NORMAL_BAM: File

  OUTVCF: string

  NCPUS: int

outputs:

  OUTPUT:
    type: File
    outputSource: mutect/mutations

steps:
  mutect:
    run: mutect.cwl
    in:
      input_normal: NORMAL_BAM
      input_tumor: TUMOR_BAM
      ncpus: NCPUS
      out_vcf: OUTVCF
    out: [mutations]
