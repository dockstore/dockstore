#!/usr/bin/env cwl-runner

class: CommandLineTool
id: Seqware-Sanger-Somatic-Workflow
label: Seqware-Sanger-Somatic-Workflow
dct:creator:
  '@id': http://sanger.ac.uk/...
  foaf:name: Keiran Raine
  foaf:mbox: mailto:keiranmraine@gmail.com
dct:contributor:
  foaf:name: Brian O'Connor
  foaf:mbox: mailto:broconno@ucsc.edu

dct:contributor:
  foaf:name: Denis Yuen
  foaf:mbox: mailto:denis.yuen@oicr.on.ca

s:author:
  - class: s:Person
    s:identifier: https://orcid.org/0000-0002-6130-1021
    s:email: mailto:dyuen@oicr.on.ca
    s:name: Denis Yuen

s:contributor:
  - class: s:Person
    s:identifier: http://orcid.org/0000-0002-7681-6415
    s:email: mailto:briandoconnor@gmail.com
    s:name: Brian O'Connor

requirements:
- class: DockerRequirement
  dockerPull: quay.io/pancancer/pcawg-sanger-cgp-workflow:2.0.3

cwlVersion: v1.0

inputs:
  tumor:
    type: File
    inputBinding:
      position: 1
      prefix: --tumor
    secondaryFiles:
    - .bai

  refFrom:
    type: File
    inputBinding:
      position: 3
      prefix: --refFrom
  bbFrom:
    type: File
    inputBinding:
      position: 4
      prefix: --bbFrom
  normal:
    type: File
    inputBinding:
      position: 2
      prefix: --normal
    secondaryFiles:
    - .bai

outputs:
  somatic_sv_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.sv.tar.gz'
  somatic_snv_mnv_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.snv_mnv.tar.gz'
  somatic_verifyBamId_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.verifyBamId.tar.gz'
  somatic_indel_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.indel.tar.gz'
  somatic_genotype_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.genotype.tar.gz'
  somatic_cnv_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.cnv.tar.gz'
  somatic_imputeCounts_tar_gz:
    type: File
    outputBinding:
      glob: '*.somatic.imputeCounts.tar.gz'
baseCommand: [/start.sh, python, /home/seqware/CGP-Somatic-Docker/scripts/run_seqware_workflow.py]
doc: "The Sanger's Cancer Genome Project core somatic calling workflow from \nthe\
  \ ICGC PanCancer Analysis of Whole Genomes (PCAWG) project.\nFor more information\
  \ see the PCAWG project [page](https://dcc.icgc.org/pcawg) and our GitHub\n[page](https://github.com/ICGC-TCGA-PanCancer)\
  \ for our code including the source for\n[this workflow](https://github.com/ICGC-TCGA-PanCancer/CGP-Somatic-Docker)."

$namespaces:
  s: https://schema.org/
  edam: http://edamontology.org/

$schemas:
 - https://schema.org/docs/schema_org_rdfa.html
 - http://edamontology.org/EDAM_1.18.owl