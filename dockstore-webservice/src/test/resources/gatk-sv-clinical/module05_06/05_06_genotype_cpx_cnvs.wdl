version 1.0 
# based on snapshot 12
# https://portal.firecloud.org/#methods/Talkowski-SV/04b_genotype_CPX_CNVs/12/wdl

# Copyright (c) 2018 Talkowski Lab

# Contact Ryan Collins <rlcollins@g.harvard.edu>

# Distributed under terms of the MIT License

import "05_06_genotype_cpx_cnvs_per_batch.wdl" as RunDepthGenotypePerBatch
import "05_06_common_mini_tasks.wdl" as MiniTasks

# Workflow to perform depth-based genotyping for a single vcf shard scattered
# across batches on predicted CPX CNVs from 04b
workflow GenotypeCpxCnvs {
  input {
    File vcf
    File gt_input_files
    Int n_per_split_small
    Int n_per_split_large
    Int n_rd_test_bins
    String prefix
    File fam_file
    String contig

    String sv_base_mini_docker
    String sv_pipeline_docker
    String sv_pipeline_rdtest_docker

    # overrides for local tasks
    RuntimeAttr? runtime_override_get_cpx_cnv_intervals
    RuntimeAttr? runtime_override_parse_genotypes

    # overrides for MiniTasks
    RuntimeAttr? runtime_override_merge_melted_gts

    # overrides for RunDepthGenotypePerBatch
    RuntimeAttr? runtime_override_split_bed_by_size
    RuntimeAttr? runtime_override_rd_genotype
    RuntimeAttr? runtime_override_concat_melted_genotypes
  }
  
  Array[Array[String]] gt_input_array = read_tsv(gt_input_files)
  String contig_prefix = prefix + "." + contig

  # Convert VCF to bed of CPX CNV intervals
  call GetCpxCnvIntervals {
    input:
      vcf=vcf,
      prefix=contig_prefix,
      sv_pipeline_docker=sv_pipeline_docker,
      runtime_attr_override=runtime_override_get_cpx_cnv_intervals
  }

  # Scatter over each batch (row) in gt_input_files and run depth genotyping
  scatter (gt_inputs in gt_input_array) {
    call RunDepthGenotypePerBatch.GenotypeCpxCnvsPerBatch as GenotypeBatch {
      input:
        cpx_bed=GetCpxCnvIntervals.cpx_cnv_bed,
        batch=gt_inputs[0],
        coverage_file=gt_inputs[1],
        coverage_file_idx=gt_inputs[2],
        rd_depth_sep_cutoff=gt_inputs[3],
        samples_list=gt_inputs[4],
        fam_file=gt_inputs[5],
        median_file=gt_inputs[6],
        n_per_split_small=n_per_split_small,
        n_per_split_large=n_per_split_large,
        n_rd_test_bins=n_rd_test_bins,
        sv_base_mini_docker=sv_base_mini_docker,
        sv_pipeline_rdtest_docker=sv_pipeline_rdtest_docker,
        runtime_override_split_bed_by_size=runtime_override_split_bed_by_size,
        runtime_override_rd_genotype=runtime_override_rd_genotype,
        runtime_override_concat_melted_genotypes=runtime_override_concat_melted_genotypes
    }
  }

  # Merge melted genotypes across all batches
  call MiniTasks.ZcatCompressedFiles as MergeMeltedGts {
    input:
      shards=GenotypeBatch.genotypes,
      outfile_name=contig_prefix + ".CPX_intervals.merged_rd_genos.bed.gz",
      filter_command="sort -Vk1,1 -k2,2n -k3,3n -k4,4V -k5,5V",
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_override_merge_melted_gts
  }

  # Parse genotyping results
  call ParseGenotypes {
    input:
      vcf=vcf,
      intervals=GetCpxCnvIntervals.cpx_cnv_bed,
      genotypes=MergeMeltedGts.outfile,
      prefix=contig_prefix,
      fam_file=fam_file,
      contig=contig,
      sv_pipeline_docker=sv_pipeline_docker,
      runtime_attr_override=runtime_override_parse_genotypes
  }

  # Final output
  output {
    File cpx_depth_gt_resolved_vcf = ParseGenotypes.cpx_depth_gt_resolved_vcf
    File reclassification_table = ParseGenotypes.reclassification_table
    File interval_genotype_counts_table = ParseGenotypes.gt_counts_table
  }
}


# Get CNV intervals from complex SV for depth genotyping
task GetCpxCnvIntervals {
  input {
    File vcf
    String prefix
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  String output_file = prefix + ".complex_CNV_intervals_to_test.bed.gz"

  # when filtering/sorting/etc, memory usage will likely go up (much of the data will have to
  # be held in memory or disk while working, potentially in a form that takes up more space)
  Float input_size = size(vcf, "GiB")
  Float compression_factor = 5.0
  Float base_disk_gb = 5.0
  Float base_mem_gb = 2.0
  RuntimeAttr runtime_default = object {
    mem_gb: base_mem_gb + compression_factor * input_size,
    disk_gb: ceil(base_disk_gb + input_size * (2.0 + 2.0 * compression_factor)),
    cpu_cores: 1,
    preemptible_tries: 3,
    max_retries: 1,
    boot_disk_gb: 10
  }
  RuntimeAttr runtime_override = select_first([runtime_attr_override, runtime_default])
  runtime {
    memory: "~{select_first([runtime_override.mem_gb, runtime_default.mem_gb])} GiB"
    disks: "local-disk ~{select_first([runtime_override.disk_gb, runtime_default.disk_gb])} HDD"
    cpu: select_first([runtime_override.cpu_cores, runtime_default.cpu_cores])
    preemptible: select_first([runtime_override.preemptible_tries, runtime_default.preemptible_tries])
    maxRetries: select_first([runtime_override.max_retries, runtime_default.max_retries])
    docker: sv_pipeline_docker
    bootDiskSizeGb: select_first([runtime_override.boot_disk_gb, runtime_default.boot_disk_gb])
  }

  command <<<
    set -eu -o pipefail
    
    /opt/sv-pipeline/04_variant_resolution/scripts/gather_cpx_intervals_for_rd_gt.sh \
      ~{vcf} \
      ~{output_file}
  >>>

  output {
    File cpx_cnv_bed = output_file
  }
}


# Parse genotyping results
task ParseGenotypes {
  input {
    File vcf
    File intervals
    File genotypes
    File fam_file
    String prefix
    String contig
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  # when filtering/sorting/etc, memory usage will likely go up (much of the data will have to
  # be held in memory or disk while working, potentially in a form that takes up more space)
  Float input_size = size([vcf, intervals, genotypes, fam_file], "GiB")
  Float compression_factor = 5.0
  Float base_disk_gb = 5.0
  Float base_mem_gb = 2.0
  RuntimeAttr runtime_default = object {
    mem_gb: base_mem_gb + compression_factor * input_size,
    disk_gb: ceil(base_disk_gb + input_size * (2.0 + 2.0 * compression_factor)),
    cpu_cores: 1,
    preemptible_tries: 3,
    max_retries: 1,
    boot_disk_gb: 10
  }
  RuntimeAttr runtime_override = select_first([runtime_attr_override, runtime_default])
  runtime {
    memory: "~{select_first([runtime_override.mem_gb, runtime_default.mem_gb])} GiB"
    disks: "local-disk ~{select_first([runtime_override.disk_gb, runtime_default.disk_gb])} HDD"
    cpu: select_first([runtime_override.cpu_cores, runtime_default.cpu_cores])
    preemptible: select_first([runtime_override.preemptible_tries, runtime_default.preemptible_tries])
    maxRetries: select_first([runtime_override.max_retries, runtime_default.max_retries])
    docker: sv_pipeline_docker
    bootDiskSizeGb: select_first([runtime_override.boot_disk_gb, runtime_default.boot_disk_gb])
  }

  command <<<
    set -eu -o pipefail
    
    /opt/sv-pipeline/04_variant_resolution/scripts/process_posthoc_cpx_depth_regenotyping.sh \
      -R ~{prefix}.CPXregenotyping_reclassification_table.~{contig}.txt \
      -G ~{prefix}.CPXregenotyping_raw_genotype_counts_table.~{contig}.txt \
      ~{vcf} \
      ~{intervals} \
      ~{genotypes} \
      ~{fam_file} \
      ~{prefix}.postCPXregenotyping.~{contig}.vcf.gz
  >>>

  output {
    File cpx_depth_gt_resolved_vcf = "~{prefix}.postCPXregenotyping.~{contig}.vcf.gz"
    File reclassification_table = "~{prefix}.CPXregenotyping_reclassification_table.~{contig}.txt"
    File gt_counts_table = "~{prefix}.CPXregenotyping_raw_genotype_counts_table.~{contig}.txt"
  }
}
