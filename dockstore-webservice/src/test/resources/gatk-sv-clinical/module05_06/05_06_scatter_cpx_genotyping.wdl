version 1.0

# based on snapshot 12
# https://portal.firecloud.org/#methods/Talkowski-SV/04b_scatter_CPX_genotyping/12/wdl

# Copyright (c) 2018 Talkowski Lab

# Contact Ryan Collins <rlcollins@g.harvard.edu>

# Distributed under terms of the MIT License

import "05_06_genotype_cpx_cnvs.wdl" as GenotypeCpx
import "05_06_common_mini_tasks.wdl" as MiniTasks

# Workflow to perform depth-based genotyping for a single vcf shard scattered 
# across batches on predicted CPX CNVs from 04b
workflow ScatterCpxGenotyping {
  input {
    File vcf
    Int n_master_vcf_shards
    Int n_master_min_vars_per_vcf_shard
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

    # overrides for MiniTasks
    RuntimeAttr? runtime_override_split_vcf_to_genotype
    RuntimeAttr? runtime_override_concat_cpx_cnv_vcfs

    # overrides for GenotypeCpx
    RuntimeAttr? runtime_override_get_cpx_cnv_intervals
    RuntimeAttr? runtime_override_parse_genotypes
    RuntimeAttr? runtime_override_merge_melted_gts
    RuntimeAttr? runtime_override_split_bed_by_size
    RuntimeAttr? runtime_override_rd_genotype
    RuntimeAttr? runtime_override_concat_melted_genotypes
  }

  String contig_prefix = prefix + "." + contig

  # Shard VCF into even slices
  call MiniTasks.SplitVcf as SplitVcfToGenotype {
    input:
      vcf=vcf,
      prefix=contig_prefix + ".shard_",
      n_shards=n_master_vcf_shards,
      min_vars_per_shard=n_master_min_vars_per_vcf_shard,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_override_split_vcf_to_genotype
  }

  # Scatter genotyping over shards
  scatter ( shard in SplitVcfToGenotype.vcf_shards ) {
    # Run genotyping
    call GenotypeCpx.GenotypeCpxCnvs as GenotypeShard {
      input:
        vcf=shard,
        gt_input_files=gt_input_files,
        n_per_split_large=n_per_split_large,
        n_per_split_small=n_per_split_small,
        n_rd_test_bins=n_rd_test_bins,
        prefix=prefix,
        fam_file=fam_file,
        contig=contig,
        sv_base_mini_docker=sv_base_mini_docker,
        sv_pipeline_docker=sv_pipeline_docker,
        sv_pipeline_rdtest_docker=sv_pipeline_rdtest_docker,
        runtime_override_get_cpx_cnv_intervals=runtime_override_get_cpx_cnv_intervals,
        runtime_override_parse_genotypes=runtime_override_parse_genotypes,
        runtime_override_merge_melted_gts=runtime_override_merge_melted_gts,
        runtime_override_split_bed_by_size=runtime_override_split_bed_by_size,
        runtime_override_rd_genotype=runtime_override_rd_genotype,
        runtime_override_concat_melted_genotypes=runtime_override_concat_melted_genotypes
    }
  }

  # Merge VCF shards
  call MiniTasks.ConcatVcfs as ConcatCpxCnvVcfs {
    input:
      vcfs=GenotypeShard.cpx_depth_gt_resolved_vcf,
      outfile_prefix=contig_prefix + ".resolved",
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_override_concat_cpx_cnv_vcfs
  }

  # Output merged VCF
  output {
    File cpx_depth_gt_resolved_vcf = ConcatCpxCnvVcfs.concat_vcf
    File cpx_depth_gt_resolved_vcf_idx = ConcatCpxCnvVcfs.concat_vcf_idx
  }
 }
