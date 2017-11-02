#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: Workflow

requirements:
 - class: StepInputExpressionRequirement
 - class: MultipleInputFeatureRequirement

inputs:
  - id: bai_path
    type: File
  - id: bam_path
    type: File
  - id: input_state
    type: string
  - id: uuid
    type: string

outputs:
  - id: merge_sqlite_destination_sqlite
    type: File
    outputSource: merge_sqlite/destination_sqlite

steps:
  - id: bai_ls_l
    run: ../../tools/ls_l.cwl
    in:
      - id: INPUT
        source: bai_path
    out:
      - id: OUTPUT

  - id: bai_md5sum
    run: ../../tools/md5sum.cwl
    in:
      - id: INPUT
        source: bai_path
    out:
      - id: OUTPUT

  - id: bai_sha256
    run: ../../tools/sha256sum.cwl
    in:
      - id: INPUT
        source: bai_path
    out:
      - id: OUTPUT

  - id: bam_ls_l
    run: ../../tools/ls_l.cwl
    in:
      - id: INPUT
        source: bam_path
    out:
      - id: OUTPUT

  - id: bam_md5sum
    run: ../../tools/md5sum.cwl
    in:
      - id: INPUT
        source: bam_path
    out:
      - id: OUTPUT

  - id: bam_sha256
    run: ../../tools/sha256sum.cwl
    in:
      - id: INPUT
        source: bam_path
    out:
      - id: OUTPUT

  - id: bai_integrity_to_db
    run: ../../tools/integrity_to_sqlite.cwl
    in:
      - id: input_state
        source: input_state
      - id: ls_l_path
        source: bai_ls_l/OUTPUT
      - id: md5sum_path
        source: bai_md5sum/OUTPUT
      - id: sha256sum_path
        source: bai_sha256/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: OUTPUT

  - id: bam_integrity_to_db
    run: ../../tools/integrity_to_sqlite.cwl
    in:
      - id: input_state
        source: input_state
      - id: ls_l_path
        source: bam_ls_l/OUTPUT
      - id: md5sum_path
        source: bam_md5sum/OUTPUT
      - id: sha256sum_path
        source: bam_sha256/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: OUTPUT

  - id: merge_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: [
        bai_integrity_to_db/OUTPUT,
        bam_integrity_to_db/OUTPUT
        ]
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log
