#!/usr/bin/env cwl-runner

description: |
  Usage:  cwl-runner <this-file-path> --bam_path <bam-path> --uuid <uuid-string>
  Options:
    --bam_path       Determine interval paths needed for picard CalculateHsMetrics
    --uuid           UUID for log file and sqlite db file

requirements:
  - import: node-engine.cwl
  - import: envvar-global.cwl
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/sra_hs_lookup

class: CommandLineTool

inputs:
  - id: "#input_bam"
    type: File
    inputBinding:
      prefix: --bam_path

  - id: "#uuid"
    type: string
    inputBinding:
      prefix: --uuid

  - id: "#bam_library_key"
    type: File
    inputBinding:
      prefix: --bam_library_key_json_path

  - id: "#key_interval"
    type: File
    inputBinding:
      prefix: --key_interval_json_path

outputs:
  - id: "#output_json"
    type: File
    description: "The json file"
    outputBinding:
      glob:
        engine: node-engine.cwl
        script: |
          {
            return $job['input_bam'].path.split('/').slice(-1)[0].slice(0,-4)+"_readgroup_interval.json";
          }

  - id: "#log"
    type: File
    description: "python log file"
    outputBinding:
      glob:
        engine: node-engine.cwl
        script: |
          {
          return $job['uuid']+"_sra_hs_lookup.log"
          }

          
baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.4/site-packages/sra_hs_lookup/main.py"]
