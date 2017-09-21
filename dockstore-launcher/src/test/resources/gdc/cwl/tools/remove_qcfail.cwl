#!/usr/bin/env cwl-runner

# example
# cwl-runner --outdir /mnt/SCRATCH/47b42e81-2500-4ebc-a0c2-acd3187cc2f0/realn/md/ --verbose remove_qcfail.cwl.yaml --first_bam /mnt/SCRATCH/47b42e81-2500-4ebc-a0c2-acd3187cc2f0/C440.TCGA-IN-8462-01A-11D-2340-08.1.bam --second_bam /mnt/SCRATCH/47b42e81-2500-4ebc-a0c2-acd3187cc2f0/realn/md/C440.TCGA-IN-8462-01A-11D-2340-08.1.bam --uuid test

description: |
  Usage:  cwl-runner --outdir <second_bam_path-dir> <this-file-path> --first_bam_path <bam-path> --second_bam_path <bam-path> --uuid <uuid-string>
  Options:
    --first_input_bam       BAM containing qcfail reads with bitmask flag
    --second_input_bam      BAM containing qcfail reads without bitmask flag
    --uuid            UUID for log file and sqlite db file

requirements:
  - import: node-engine.cwl
  - import: envvar-global.cwl
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/remove_qcfailed_mapped

class: CommandLineTool

inputs:
  - id: "#first_input_bam"
    type: File
    inputBinding:
      prefix: --first_bam
      secondaryFiles:
        - engine: node-engine.cwl
          script: |
            {
              return {"path": $job['first_input_bam'].path.slice(0,-4)+".bai", "class": "File"};
            }

  - id: "#second_input_bam"
    type: File
    inputBinding:
      prefix: --second_bam
      secondaryFiles:
        - engine: node-engine.cwl
          script: |
            {
              return {"path": $job['second_input_bam'].path.slice(0,-4)+".bai", "class": "File"};
            }

  - id: "#uuid"
    type: string
    inputBinding:
      prefix: --uuid

outputs:
  - id: "#output_bam"
    type: File
    description: "The second bam file without qcfail reads"
    outputBinding:
      glob:
        engine: node-engine.cwl
        script: |
          {
            return "remove_qcfail/"+$job['second_input_bam'].path.split('/').slice(-1)[0];
          }

  - id: "#output_sqlite"
    type: File
    description: "sqlite file"
    outputBinding:
      glob:
        engine: node-engine.cwl
        script: |
          {
          return $job['uuid']+".db"
          }

  - id: "#log"
    type: File
    description: "python log file"
    outputBinding:
      glob:
        engine: node-engine.cwl
        script: |
          {
          return $job['uuid']+"_remove_qcfail.log"
          }

baseCommand: ["/home/ubuntu/.virtualenvs/p3/bin/python","/home/ubuntu/.virtualenvs/p3/lib/python3.4/site-packages/remove_qcfailed_mapped/main.py"]
