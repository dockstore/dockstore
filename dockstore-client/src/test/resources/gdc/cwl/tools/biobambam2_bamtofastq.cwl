#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/biobambam:1
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: collate
    type: int
    default: 1
    inputBinding:
      prefix: collate=
      separate: false

  - id: exclude
    type: string
    default: QCFAIL,SECONDARY,SUPPLEMENTARY
    inputBinding:
      prefix: exclude=
      separate: false

  - id: filename
    format: "edam:format_2572"
    type: File
    inputBinding:
      prefix: filename=
      separate: false

  - id: gz
    type: int
    default: 1
    inputBinding:
      prefix: gz=
      separate: false

  - id: inputformat
    type: string
    default: "bam"
    inputBinding:
      prefix: inputformat=
      separate: false

  - id: level
    type: int
    default: 5
    inputBinding:
      prefix: level=
      separate: false

  - id: outputdir
    type: string
    default: .
    inputBinding:
      prefix: outputdir=
      separate: false

  - id: outputperreadgroup
    type: int
    default: 1
    inputBinding:
      prefix: outputperreadgroup=
      separate: false

  - id: outputperreadgroupsuffixF
    type: string
    default: _1.fq.gz
    inputBinding:
      prefix: outputperreadgroupsuffixF=
      separate: false

  - id: outputperreadgroupsuffixF2
    type: string
    default: _2.fq.gz
    inputBinding:
      prefix: outputperreadgroupsuffixF2=
      separate: false

  - id: outputperreadgroupsuffixO
    type: string
    default: _o1.fq.gz
    inputBinding:
      prefix: outputperreadgroupsuffixO=
      separate: false

  - id: outputperreadgroupsuffixO2
    type: string
    default: _o2.fq.gz
    inputBinding:
      prefix: outputperreadgroupsuffixO2=
      separate: false

  - id: outputperreadgroupsuffixS
    type: string
    default: _s.fq.gz
    inputBinding:
      prefix: outputperreadgroupsuffixS=
      separate: false

  - id: tryoq
    type: int
    default: 1
    inputBinding:
      prefix: tryoq=
      separate: false

  - id: T
    type: string
    default: tempfq
    inputBinding:
      prefix: T=
      separate: false

outputs:
  - id: output_fastq1
    format: "edam:format_2182"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*_1.fq.gz"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

  - id: output_fastq2
    format: "edam:format_2182"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*_2.fq.gz"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

  - id: output_fastq_o1
    format: "edam:format_2182"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*_o1.fq.gz"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

  - id: output_fastq_o2
    format: "edam:format_2182"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*_o2.fq.gz"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

  - id: output_fastq_s
    format: "edam:format_2182"
    type:
      type: array
      items: File
    outputBinding:
      glob: "*_s.fq.gz"
      outputEval: |
        ${ return self.sort(function(a,b) { return a.location > b.location ? 1 : (a.location < b.location ? -1 : 0) }) }

baseCommand: [/usr/local/bin/bamtofastq]
