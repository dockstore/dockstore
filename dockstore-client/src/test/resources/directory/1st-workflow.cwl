class: Workflow
cwlVersion: v1.0
hints: []
inputs:
- id: inp
  type: File
- id: ex
  type: string
- id: description
  type:
    items: string
    type: array
- id: config__algorithm__variant_regions
  type:
    items:
    - 'null'
    - string
    type: array
- id: reference__viral
  secondaryFiles:
  - .fai
  - ^.dict
  type:
    items:
      items: File
      type: array
    type: array
- id: reference__fasta__base
  secondaryFiles:
  - .fai
  type:
    items:
      items: File
      type: array
    type: array
- id: reference__genome_context
  secondaryFiles:
  - .tbi
  type:
    items:
      items: File
      type: array
    type: array
outputs:
  classout:
    type: File
    outputSource: untar/example_out
requirements:
- class: EnvVarRequirement
  envDef:
  - envName: MPLCONFIGDIR
    envValue: .
- class: ScatterFeatureRequirement
- class: SubworkflowFeatureRequirement
steps:
- id: prep_samples_to_rec
  in:
  - id: config__algorithm__variant_regions
    source: config__algorithm__variant_regions
  - id: un_reference__fasta__base
    source: reference__fasta__base
  - id: description
    source: description
  - id : reference__genome_context
    source: reference__genome_context
  out:
  - id: prep_samples_rec
  run: steps/prep_samples_to_rec.cwl
- id: untar
  in:
    - id: tarfile
      source: inp
    - id: extractfile
      source: ex
  out: [example_out]
  run: subDirectory/tar-param.cwl

