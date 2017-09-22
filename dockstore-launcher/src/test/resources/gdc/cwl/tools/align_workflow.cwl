#!/usr/bin/env cwl-runner

- id: bamtoreadgroup_cmd
  class: CommandLineTool
  requirements:
    - class: ShellCommandRequirement
  inputs:
    - id: "#bam_path"
      type: File
      inputBinding:
        position: 2
  outputs:
    - id: "#output_readgroup"
      type:
        type: array
        items: File
      outputBinding:
        glob: "*.readgroup"
        outputEval:
          engine: node-engine.cwl
          script: |
            {
            self.sort(function(a,b) { return a.path > b.path });
            return self;
            }
  arguments:
    - valueFrom: ".readgroup"
      position: 1
      shellQuote: false
    - valueFrom: "|"
      position: 3
      shellQuote: false
    - valueFrom: "xargs"
      position: 4
      shellQuote: false
    - valueFrom: "touch"
      position: 5
      shellQuote: false
  baseCommand: grep



- id: bamtofastq_cmd
  class: CommandLineTool
  requirements:
    - class: ShellCommandRequirement
  inputs:
    - id: "#bam_path"
      type: File
      inputBinding:
        position: 2
  outputs:
    - id: "#output_fastq1"
      type:
        type: array
        items: File
      outputBinding:
        glob: "*_1.fq"
        outputEval:
          engine: node-engine.cwl
          script: |
            {
            self.sort(function(a,b) { return a.path > b.path });
            return self;
            }
    - id: "#output_fastq2"
      type:
        type: array
        items: File
      outputBinding:
        glob: "*_2.fq"
        outputEval:
          engine: node-engine.cwl
          script: |
            {
            self.sort(function(a,b) { return a.path > b.path });
            return self;
            }
  arguments:
    - valueFrom: ".fq"
      position: 1
      shellQuote: false
    - valueFrom: "|"
      position: 3
      shellQuote: false
    - valueFrom: "xargs"
      position: 4
      shellQuote: false
    - valueFrom: "touch"
      position: 5
      shellQuote: false
  baseCommand: grep


- id: align_cmd
  class: CommandLineTool
  requirements:
    - class: ShellCommandRequirement
    - class: InlineJavascriptRequirement
    - import: node-engine.cwl
    - import: envvar-global.cwl
  inputs:
    - id: "#fastq1_path"
      type: File
    - id: "#fastq2_path"
      type: File
    - id: "#readgroup_path"
      type: File
  outputs:
    - id: "#output_bam"
      type: File
      outputBinding:
        glob:
          engine: node-engine.cwl
          script: |
            {
            return inputs.readgroup_path.path.split('/').slice(-1)[0].slice(0,-10)+"_realign.bam";
            }
  arguments:
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return '"' + inputs.fastq1_path.path.split('/').slice(-1)[0] + '"';
          }
      position: 1
      shellQuote: false
    - valueFrom: ">"
      position: 2
      shellQuote: false
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return inputs.readgroup_path.path.split('/').slice(-1)[0].slice(0,-10)+"_realign.bam";
          }
      position: 3
      shellQuote: false
    - valueFrom: "&&"
      position: 4
      shellQuote: false
    - valueFrom: echo
      position: 5
      shellQuote: false
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return '"' + inputs.fastq2_path.path.split('/').slice(-1)[0] + '"';
          }
      position: 6
      shellQuote: false
    - valueFrom: ">>"
      position: 7
      shellQuote: false
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return inputs.readgroup_path.path.split('/').slice(-1)[0].slice(0,-10)+"_realign.bam";
          }
      position: 8
      shellQuote: false
    - valueFrom: "&&"
      position: 9
      shellQuote: false
    - valueFrom: echo
      position: 10
      shellQuote: false
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return '"' + inputs.readgroup_path.path.split('/').slice(-1)[0] + '"';
          }
      position: 11
      shellQuote: false
    - valueFrom: ">>"
      position: 12
      shellQuote: false
    - valueFrom:
        engine: node-engine.cwl
        script: |
          {
          return inputs.readgroup_path.path.split('/').slice(-1)[0].slice(0,-10)+"_realign.bam";
          }
      position: 13
      shellQuote: false
  baseCommand: echo



- id: main
  class: Workflow
  requirements:
    - class: ScatterFeatureRequirement
    - class: StepInputExpressionRequirement
    - class: InlineJavascriptRequirement
    - import: node-engine.cwl
    - import: envvar-global.cwl
  inputs:
    - id: "#bam_path"
      type: File
  outputs:
    - id: "align_output_bam"
      type:
        type: array
        items: File
      source: "#align.output_bam"
  steps:
    - id: "#bamtoreadgroup"
      run: "#bamtoreadgroup_cmd"
      inputs:
        - id: "#bamtoreadgroup.bam_path"
          source: "#bam_path"
      outputs:
        - id: "#bamtoreadgroup.output_readgroup"
    - id: "#bamtofastq"
      run: "#bamtofastq_cmd"
      inputs:
        - id: "#bamtofastq.bam_path"
          source: "#bam_path"
      outputs:
        - id: "#bamtofastq.output_fastq1"
        - id: "#bamtofastq.output_fastq2"          
    - id: "#align"
      run: "#align_cmd"
      scatter: ["#align.fastq1_path", "#align.fastq2_path", "#align.readgroup_path"]
      scatterMethod: "dotproduct"
      inputs:
        - id: "#align.fastq1_path"
          source: "#bamtofastq.output_fastq1"
        - id: "#align.fastq2_path"
          source: "#bamtofastq.output_fastq2"
        - id: "#align.readgroup_path"
          source: "#bamtoreadgroup.output_readgroup"
      outputs:
        - id: "#align.output_bam"
