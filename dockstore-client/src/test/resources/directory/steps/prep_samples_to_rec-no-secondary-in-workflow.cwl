arguments:
- position: 0
  valueFrom: sentinel_runtime=cores,$(runtime['cores']),ram,$(runtime['ram'])
- sentinel_parallel=multi-combined
- sentinel_outputs=prep_samples_rec:description;reference__fasta__base;config__algorithm__variant_regions
- sentinel_inputs=config__algorithm__variant_regions:var,reference__fasta__base:var,description:var
baseCommand:
- pwd
class: CommandLineTool
cwlVersion: v1.0
hints:
- class: DockerRequirement
  dockerImageId: quay.io/bcbio/bcbio-base
  dockerPull: quay.io/bcbio/bcbio-base
- class: ResourceRequirement
  coresMin: 1
  outdirMin: 1024
  ramMin: 3072
inputs:
- id: config__algorithm__variant_regions
  type:
    items:
    - 'null'
    - string
    type: array
- id: un_reference__fasta__base
  secondaryFiles:
  - .fai
  - ^.dict
  type:
    items:
      items: File
      type: array
    type: array
- id: description
  type:
    items: string
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
- id: prep_samples_rec
  type:
    items:
      fields:
      - name: description
        type: string
      - name: reference__fasta__base
        type: File
      - name: config__algorithm__variant_regions
        type:
        - 'null'
        - string
      name: prep_samples_rec
      type: record
    type: array
requirements:
- class: InlineJavascriptRequirement
- class: InitialWorkDirRequirement
  listing:
  - entry: $(JSON.stringify(inputs))
    entryname: cwl.inputs.json
