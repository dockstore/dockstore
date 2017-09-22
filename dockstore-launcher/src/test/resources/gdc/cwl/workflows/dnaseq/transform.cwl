#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: Workflow

requirements:
  - class: InlineJavascriptRequirement
  - class: MultipleInputFeatureRequirement
  - class: ScatterFeatureRequirement
  - class: StepInputExpressionRequirement
  - class: SubworkflowFeatureRequirement

inputs:
  - id: bam_path
    type: File
  - id: db_snp_path
    type: File
  - id: reference_fasta_path
    type: File
  - id: thread_count
    type: int
  - id: uuid
    type: string

outputs:
  - id: picard_markduplicates_output
    type: File
    outputSource: picard_markduplicates/OUTPUT
  - id: merge_all_sqlite_destination_sqlite
    type: File
    outputSource: merge_all_sqlite/destination_sqlite

steps:
  - id: samtools_bamtobam
    run: ../../tools/samtools_bamtobam.cwl
    in:
      - id: INPUT
        source: bam_path
    out:
      - id: OUTPUT

  - id: picard_validatesamfile_original
    run: ../../tools/picard_validatesamfile.cwl
    in:
      - id: INPUT
        source: samtools_bamtobam/OUTPUT
      - id: VALIDATION_STRINGENCY
        valueFrom: "LENIENT"
    out:
      - id: OUTPUT

  # need eof and dup QNAME detection
  - id: picard_validatesamfile_original_to_sqlite
    run: ../../tools/picard_validatesamfile_to_sqlite.cwl
    in:
      - id: bam
        source: bam_path
        valueFrom: $(self.basename)
      - id: input_state
        valueFrom: "original"
      - id: metric_path
        source: picard_validatesamfile_original/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: sqlite

  - id: biobambam_bamtofastq
    run: ../../tools/biobambam2_bamtofastq.cwl
    in:
      - id: filename
        source: samtools_bamtobam/OUTPUT
    out:
      - id: output_fastq1
      - id: output_fastq2
      - id: output_fastq_o1
      - id: output_fastq_o2
      - id: output_fastq_s

  - id: remove_duplicate_fastq1
    run: ../../tools/fastq_remove_duplicate_qname.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: biobambam_bamtofastq/output_fastq1
    out:
      - id: OUTPUT

  - id: remove_duplicate_fastq2
    run: ../../tools/fastq_remove_duplicate_qname.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: biobambam_bamtofastq/output_fastq2
    out:
      - id: OUTPUT

  - id: remove_duplicate_fastq_o1
    run: ../../tools/fastq_remove_duplicate_qname.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: biobambam_bamtofastq/output_fastq_o1
    out:
      - id: OUTPUT

  - id: remove_duplicate_fastq_o2
    run: ../../tools/fastq_remove_duplicate_qname.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: biobambam_bamtofastq/output_fastq_o2
    out:
      - id: OUTPUT

  - id: remove_duplicate_fastq_s
    run: ../../tools/fastq_remove_duplicate_qname.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: biobambam_bamtofastq/output_fastq_s
    out:
      - id: OUTPUT

  - id: sort_scattered_fastq1
    run: ../../tools/sort_scatter_expression.cwl
    in:
      - id: INPUT
        source: remove_duplicate_fastq1/OUTPUT
    out:
      - id: OUTPUT

  - id: sort_scattered_fastq2
    run: ../../tools/sort_scatter_expression.cwl
    in:
      - id: INPUT
        source: remove_duplicate_fastq2/OUTPUT
    out:
      - id: OUTPUT

  - id: sort_scattered_fastq_o1
    run: ../../tools/sort_scatter_expression.cwl
    in:
      - id: INPUT
        source: remove_duplicate_fastq_o1/OUTPUT
    out:
      - id: OUTPUT

  - id: sort_scattered_fastq_o2
    run: ../../tools/sort_scatter_expression.cwl
    in:
      - id: INPUT
        source: remove_duplicate_fastq_o2/OUTPUT
    out:
      - id: OUTPUT

  - id: sort_scattered_fastq_s
    run: ../../tools/sort_scatter_expression.cwl
    in:
      - id: INPUT
        source: remove_duplicate_fastq_s/OUTPUT
    out:
      - id: OUTPUT

  - id: bam_readgroup_to_json
    run: ../../tools/bam_readgroup_to_json.cwl
    in:
      - id: INPUT
        source: samtools_bamtobam/OUTPUT
      - id: MODE
        valueFrom: "lenient"
    out:
      - id: OUTPUT

  - id: readgroup_json_db
    run: ../../tools/readgroup_json_db.cwl
    scatter: json_path
    in:
      - id: json_path
        source: bam_readgroup_to_json/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: log
      - id: output_sqlite

  - id: merge_readgroup_json_db
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: readgroup_json_db/output_sqlite
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: fastqc1
    run: ../../tools/fastqc.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: sort_scattered_fastq1/OUTPUT
      - id: threads
        source: thread_count
    out:
      - id: OUTPUT

  - id: fastqc2
    run: ../../tools/fastqc.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: sort_scattered_fastq2/OUTPUT
      - id: threads
        source: thread_count
    out:
      - id: OUTPUT

  - id: fastqc_s
    run: ../../tools/fastqc.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: sort_scattered_fastq_s/OUTPUT
      - id: threads
        source: thread_count
    out:
      - id: OUTPUT

  - id: fastqc_o1
    run: ../../tools/fastqc.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: sort_scattered_fastq_o1/OUTPUT
      - id: threads
        source: thread_count
    out:
      - id: OUTPUT

  - id: fastqc_o2
    run: ../../tools/fastqc.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: sort_scattered_fastq_o2/OUTPUT
      - id: threads
        source: thread_count
    out:
      - id: OUTPUT

  - id: fastqc_db1
    run: ../../tools/fastqc_db.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: fastqc1/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: LOG
      - id: OUTPUT

  - id: fastqc_db2
    run: ../../tools/fastqc_db.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: fastqc2/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: LOG
      - id: OUTPUT

  - id: fastqc_db_s
    run: ../../tools/fastqc_db.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: fastqc_s/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: LOG
      - id: OUTPUT

  - id: fastqc_db_o1
    run: ../../tools/fastqc_db.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: fastqc_o1/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: LOG
      - id: OUTPUT

  - id: fastqc_db_o2
    run: ../../tools/fastqc_db.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: fastqc_o2/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: LOG
      - id: OUTPUT

  - id: merge_fastqc_db1_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: fastqc_db1/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: merge_fastqc_db2_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: fastqc_db2/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: merge_fastqc_db_s_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: fastqc_db_s/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: merge_fastqc_db_o1_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: fastqc_db_o1/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: merge_fastqc_db_o2_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: fastqc_db_o2/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: fastqc_pe_basicstats_json
    run: ../../tools/fastqc_basicstatistics_json.cwl
    in:
      - id: sqlite_path
        source: merge_fastqc_db1_sqlite/destination_sqlite
    out:
      - id: OUTPUT

  - id: fastqc_se_basicstats_json
    run: ../../tools/fastqc_basicstatistics_json.cwl
    in:
      - id: sqlite_path
        source: merge_fastqc_db_s_sqlite/destination_sqlite
    out:
      - id: OUTPUT

  - id: fastqc_o1_basicstats_json
    run: ../../tools/fastqc_basicstatistics_json.cwl
    in:
      - id: sqlite_path
        source: merge_fastqc_db_o1_sqlite/destination_sqlite
    out:
      - id: OUTPUT

  - id: fastqc_o2_basicstats_json
    run: ../../tools/fastqc_basicstatistics_json.cwl
    in:
      - id: sqlite_path
        source: merge_fastqc_db_o2_sqlite/destination_sqlite
    out:
      - id: OUTPUT

  - id: decider_bwa_pe
    run: ../../tools/decider_bwa_expression.cwl
    in:
      - id: fastq_path
        source: sort_scattered_fastq1/OUTPUT
      - id: readgroup_path
        source: bam_readgroup_to_json/OUTPUT
    out:
      - id: output_readgroup_paths

  - id: decider_bwa_se
    run: ../../tools/decider_bwa_expression.cwl
    in:
      - id: fastq_path
        source: sort_scattered_fastq_s/OUTPUT
      - id: readgroup_path
        source: bam_readgroup_to_json/OUTPUT
    out:
      - id: output_readgroup_paths

  - id: decider_bwa_o1
    run: ../../tools/decider_bwa_expression.cwl
    in:
      - id: fastq_path
        source: sort_scattered_fastq_o1/OUTPUT
      - id: readgroup_path
        source: bam_readgroup_to_json/OUTPUT
    out:
      - id: output_readgroup_paths

  - id: decider_bwa_o2
    run: ../../tools/decider_bwa_expression.cwl
    in:
      - id: fastq_path
        source: sort_scattered_fastq_o2/OUTPUT
      - id: readgroup_path
        source: bam_readgroup_to_json/OUTPUT
    out:
      - id: output_readgroup_paths

  - id: bwa_pe
    run: ../../tools/bwa_pe.cwl
    scatter: [fastq1, fastq2, readgroup_json_path]
    scatterMethod: "dotproduct"
    in:
      - id: fasta
        source: reference_fasta_path
      - id: fastq1
        source: sort_scattered_fastq1/OUTPUT
      - id: fastq2
        source: sort_scattered_fastq2/OUTPUT
      - id: readgroup_json_path
        source: decider_bwa_pe/output_readgroup_paths
      - id: fastqc_json_path
        source: fastqc_pe_basicstats_json/OUTPUT
      - id: thread_count
        source: thread_count
    out:
      - id: OUTPUT

  - id: bwa_se
    run: ../../tools/bwa_se.cwl
    scatter: [fastq, readgroup_json_path]
    scatterMethod: "dotproduct"
    in:
      - id: fasta
        source: reference_fasta_path
      - id: fastq
        source: sort_scattered_fastq_s/OUTPUT
      - id: readgroup_json_path
        source: decider_bwa_se/output_readgroup_paths
      - id: fastqc_json_path
        source: fastqc_se_basicstats_json/OUTPUT
      - id: thread_count
        source: thread_count
    out:
      - id: OUTPUT

  - id: bwa_o1
    run: ../../tools/bwa_se.cwl
    scatter: [fastq, readgroup_json_path]
    scatterMethod: "dotproduct"
    in:
      - id: fasta
        source: reference_fasta_path
      - id: fastq
        source: sort_scattered_fastq_o1/OUTPUT
      - id: readgroup_json_path
        source: decider_bwa_o1/output_readgroup_paths
      - id: fastqc_json_path
        source: fastqc_o1_basicstats_json/OUTPUT
      - id: thread_count
        source: thread_count
    out:
      - id: OUTPUT

  - id: bwa_o2
    run: ../../tools/bwa_se.cwl
    scatter: [fastq, readgroup_json_path]
    scatterMethod: "dotproduct"
    in:
      - id: fasta
        source: reference_fasta_path
      - id: fastq
        source: sort_scattered_fastq_o2/OUTPUT
      - id: readgroup_json_path
        source: decider_bwa_o2/output_readgroup_paths
      - id: fastqc_json_path
        source: fastqc_o2_basicstats_json/OUTPUT
      - id: thread_count
        source: thread_count
    out:
      - id: OUTPUT

  - id: picard_sortsam_pe
    run: ../../tools/picard_sortsam.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: bwa_pe/OUTPUT
      - id: OUTPUT
        valueFrom: $(inputs.INPUT.basename)
    out:
      - id: SORTED_OUTPUT

  - id: picard_sortsam_se
    run: ../../tools/picard_sortsam.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: bwa_se/OUTPUT
      - id: OUTPUT
        valueFrom: $(inputs.INPUT.basename)
    out:
      - id: SORTED_OUTPUT

  - id: picard_sortsam_o1
    run: ../../tools/picard_sortsam.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: bwa_o1/OUTPUT
      - id: OUTPUT
        valueFrom: $(inputs.INPUT.basename)
    out:
      - id: SORTED_OUTPUT

  - id: picard_sortsam_o2
    run: ../../tools/picard_sortsam.cwl
    scatter: INPUT
    in:
      - id: INPUT
        source: bwa_o2/OUTPUT
      - id: OUTPUT
        valueFrom: $(inputs.INPUT.basename)
    out:
      - id: SORTED_OUTPUT

  - id: metrics_pe
    run: metrics.cwl
    scatter: bam
    in:
      - id: bam
        source: picard_sortsam_pe/SORTED_OUTPUT
      - id: db_snp_vcf
        source: db_snp_path
      - id: fasta
        source: reference_fasta_path
      - id: input_state
        valueFrom: "sorted_readgroup"
      - id: parent_bam
        source: bam_path
        valueFrom: $(self.basename)
      - id: thread_count
        source: thread_count
      - id: uuid
        source: uuid
    out:
      - id: merge_sqlite_destination_sqlite

  - id: merge_metrics_pe
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: metrics_pe/merge_sqlite_destination_sqlite
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: metrics_se
    run: metrics.cwl
    scatter: bam
    in:
      - id: bam
        source: picard_sortsam_se/SORTED_OUTPUT
      - id: db_snp_vcf
        source: db_snp_path
      - id: fasta
        source: reference_fasta_path
      - id: input_state
        valueFrom: "sorted_readgroup"
      - id: parent_bam
        source: bam_path
        valueFrom: $(self.basename)
      - id: thread_count
        source: thread_count
      - id: uuid
        source: uuid
    out:
      - id: merge_sqlite_destination_sqlite

  - id: merge_metrics_se
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: metrics_se/merge_sqlite_destination_sqlite
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log

  - id: picard_mergesamfiles_pe
    run: ../../tools/picard_mergesamfiles.cwl
    in:
      - id: INPUT
        source: picard_sortsam_pe/SORTED_OUTPUT
      - id: OUTPUT
        source: bam_path
        valueFrom: $(self.basename)
    out:
      - id: MERGED_OUTPUT

  - id: picard_mergesamfiles_se
    run: ../../tools/picard_mergesamfiles.cwl
    in:
      - id: INPUT
        source: picard_sortsam_se/SORTED_OUTPUT
      - id: OUTPUT
        source: bam_path
        valueFrom: $(self.basename)
    out:
      - id: MERGED_OUTPUT

  - id: picard_mergesamfiles_o1
    run: ../../tools/picard_mergesamfiles.cwl
    in:
      - id: INPUT
        source: picard_sortsam_o1/SORTED_OUTPUT
      - id: OUTPUT
        source: bam_path
        valueFrom: $(self.basename)
    out:
      - id: MERGED_OUTPUT

  - id: picard_mergesamfiles_o2
    run: ../../tools/picard_mergesamfiles.cwl
    in:
      - id: INPUT
        source: picard_sortsam_o2/SORTED_OUTPUT
      - id: OUTPUT
        source: bam_path
        valueFrom: $(self.basename)
    out:
      - id: MERGED_OUTPUT

  - id: picard_mergesamfiles
    run: ../../tools/picard_mergesamfiles.cwl
    in:
      - id: INPUT
        source: [
        picard_mergesamfiles_pe/MERGED_OUTPUT,
        picard_mergesamfiles_se/MERGED_OUTPUT,
        picard_mergesamfiles_o1/MERGED_OUTPUT,
        picard_mergesamfiles_o2/MERGED_OUTPUT
        ]
      - id: OUTPUT
        source: bam_path
        valueFrom: $(self.basename.slice(0,-4) + "_gdc_realn.bam")
    out:
      - id: MERGED_OUTPUT

  - id: bam_reheader
    run: ../../tools/bam_reheader.cwl
    in:
      - id: bam_path
        source: picard_mergesamfiles/MERGED_OUTPUT
    out:
      - id: output_bam

  - id: picard_markduplicates
    run: ../../tools/picard_markduplicates.cwl
    in:
      - id: INPUT
        source: bam_reheader/output_bam
    out:
      - id: OUTPUT
      - id: METRICS

  - id: picard_markduplicates_to_sqlite
    run: ../../tools/picard_markduplicates_to_sqlite.cwl
    in:
      - id: bam
        source: picard_markduplicates/OUTPUT
        valueFrom: $(self.basename)
      - id: input_state
        valueFrom: "markduplicates_readgroups"
      - id: metric_path
        source: picard_markduplicates/METRICS
      - id: uuid
        source: uuid
    out:
      - id: sqlite

  - id: picard_validatesamfile_markduplicates
    run: ../../tools/picard_validatesamfile.cwl
    in:
      - id: INPUT
        source: picard_markduplicates/OUTPUT
      - id: VALIDATION_STRINGENCY
        valueFrom: "STRICT"
    out:
      - id: OUTPUT

  #need eof and dup QNAME detection
  - id: picard_validatesamfile_markdupl_to_sqlite
    run: ../../tools/picard_validatesamfile_to_sqlite.cwl
    in:
      - id: bam
        source: bam_path
        valueFrom: $(self.basename)
      - id: input_state
        valueFrom: "markduplicates_readgroups"
      - id: metric_path
        source: picard_validatesamfile_markduplicates/OUTPUT
      - id: uuid
        source: uuid
    out:
      - id: sqlite

  - id: metrics_markduplicates
    run: mixed_library_metrics.cwl
    in:
      - id: bam
        source: picard_markduplicates/OUTPUT
      - id: db_snp_vcf
        source: db_snp_path
      - id: fasta
        source: reference_fasta_path
      - id: input_state
        valueFrom: "markduplicates_readgroups"
      - id: thread_count
        source: thread_count
      - id: uuid
        source: uuid
    out:
      - id: merge_sqlite_destination_sqlite

  - id: integrity
    run: integrity.cwl
    in:
      - id: bai_path
        source: picard_markduplicates/OUTPUT
        valueFrom: $(self.secondaryFiles[0])
      - id: bam_path
        source: picard_markduplicates/OUTPUT
      - id: input_state
        valueFrom: "markduplicates_readgroups"
      - id: uuid
        source: uuid
    out:
      - id: merge_sqlite_destination_sqlite

  - id: merge_all_sqlite
    run: ../../tools/merge_sqlite.cwl
    in:
      - id: source_sqlite
        source: [
          picard_validatesamfile_original_to_sqlite/sqlite,
          picard_validatesamfile_markdupl_to_sqlite/sqlite,
          merge_readgroup_json_db/destination_sqlite,
          merge_fastqc_db1_sqlite/destination_sqlite,
          merge_fastqc_db2_sqlite/destination_sqlite,
          merge_fastqc_db_s_sqlite/destination_sqlite,
          merge_fastqc_db_o1_sqlite/destination_sqlite,
          merge_fastqc_db_o2_sqlite/destination_sqlite,
          merge_metrics_pe/destination_sqlite,
          merge_metrics_se/destination_sqlite,
          metrics_markduplicates/merge_sqlite_destination_sqlite,
          picard_markduplicates_to_sqlite/sqlite,
          integrity/merge_sqlite_destination_sqlite
        ]
      - id: uuid
        source: uuid
    out:
      - id: destination_sqlite
      - id: log
