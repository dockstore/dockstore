version 1.0

import "Module00a.wdl" as m00a
import "Module00b.wdl" as m00b
import "PloidyEstimation.wdl" as pe
import "Module00c.wdl" as m00c
import "DepthPreprocessing.wdl" as dpn
import "Module01.wdl" as m01
import "Module02.wdl" as m02
import "SRTest.wdl" as SRTest
import "Module03.wdl" as m03
import "Module04.wdl" as m04
import "Module05_06.wdl" as m0506
import "GermlineCNVCase.wdl" as gcnv
import "SingleSampleFiltering.wdl" as SingleSampleFiltering
import "GATKSVPipelineSingleSampleMetrics.wdl" as SingleSampleMetrics
import "Utils.wdl" as utils
import "Structs.wdl"

# GATK SV Pipeline single sample mode
# Runs Modules 00abc, 01, 03.MergePesrVcfs, 04, 05/06

workflow GATKSVPipelineSingleSample {
  input {
    # Batch info
    String batch
    String sample_id

    # Global files
    File ref_ped_file
    File genome_file
    File primary_contigs_list
    File primary_contigs_fai
    String ref_build        # Needs to be GRCh37 or hg38
    File reference_fasta
    File reference_index    # Index (.fai), must be in same dir as fasta
    File reference_dict     # Dictionary (.dict), must be in same dir as fasta
    File ref_panel_vcf
    File autosome_file      # fai of autosomal contigs
    File allosome_file      # fai of allosomal contigs

    String sv_base_mini_docker
    String sv_base_docker
    String sv_pipeline_docker
    String sv_pipeline_rdtest_docker
    String sv_pipeline_base_docker
    String sv_pipeline_qc_docker
    String linux_docker
    String cnmops_docker
    String gatk_docker
    String condense_counts_docker
    String genomes_in_the_cloud_docker
    String samtools_cloud_docker
    String manta_docker
    String wham_docker

    ############################################################
    ## Module 00a
    ############################################################

    File bam_or_cram_file
    File bam_or_cram_index

    # Use only for crams in requester pays buckets
    Boolean requester_pays_crams = false

    # Common parameters
    String? reference_version   # Either "38" or "19"

    # Coverage collection inputs
    Int? bin_length
    File? coverage_blacklist
    Int? mem_gb_for_preprocess_intervals

    # Manta inputs
    File manta_region_bed
    File? manta_region_bed_index
    Float? manta_jobs_per_cpu
    Int? manta_mem_gb_per_job

    # Wham inputs
    File wham_whitelist_bed_file

    # Runtime configuration overrides
    RuntimeAttr? runtime_attr_baf
    RuntimeAttr? runtime_attr_baf_gather
    RuntimeAttr? runtime_attr_cram_to_bam
    RuntimeAttr? runtime_attr_manta
    RuntimeAttr? runtime_attr_pesr
    RuntimeAttr? runtime_attr_wham
    RuntimeAttr? runtime_attr_wham_whitelist

    ############################################################
    ## Module 00b
    ############################################################

    # Optional QC tasks
    Boolean run_vcf_qc

    # WGD files
    File wgd_scoring_mask

    RuntimeAttr? runtime_attr_qc
    RuntimeAttr? runtime_attr_qc_outlier
    RuntimeAttr? ploidy_score_runtime_attr
    RuntimeAttr? ploidy_build_runtime_attr
    RuntimeAttr? wgd_build_runtime_attr
    RuntimeAttr? wgd_score_runtime_attr

    ############################################################
    ## Module 00c
    ############################################################

    # Parameters
    File inclusion_bed
    Int min_svsize                  # Minimum SV length to include

    # gCNV inputs
    File contig_ploidy_model_tar
    Array[File] gcnv_model_tars

    # list of sample in the reference panel
    Array[String] ref_samples

    # bincov counts files (for cn.mops)
    File ref_panel_bincov_matrix

    Array[File] ref_pesr_disc_files
    Array[File] ref_pesr_split_files

    File? gatk4_jar_override
    Float? gcnv_p_alt
    Float? gcnv_cnv_coherence_length
    Int? gcnv_max_copy_number

    Float? gcnv_mapping_error_rate
    Float? gcnv_sample_psi_scale
    Float? gcnv_depth_correction_tau
    String? gcnv_copy_number_posterior_expectation_mode
    Int? gcnv_active_class_padding_hybrid_mode

    Float? gcnv_learning_rate
    Float? gcnv_adamax_beta_1
    Float? gcnv_adamax_beta_2
    Int? gcnv_log_emission_samples_per_round
    Float? gcnv_log_emission_sampling_median_rel_error
    Int? gcnv_log_emission_sampling_rounds
    Int? gcnv_max_advi_iter_first_epoch
    Int? gcnv_max_advi_iter_subsequent_epochs
    Int? gcnv_min_training_epochs
    Int? gcnv_max_training_epochs
    Float? gcnv_initial_temperature
    Int? gcnv_num_thermal_advi_iters
    Int? gcnv_convergence_snr_averaging_window
    Float? gcnv_convergence_snr_trigger_threshold
    Int? gcnv_convergence_snr_countdown_window
    Int? gcnv_max_calling_iters
    Float? gcnv_caller_update_convergence_threshold
    Float? gcnv_caller_internal_admixing_rate
    Float? gcnv_caller_external_admixing_rate
    Boolean? gcnv_disable_annealing

    Int ref_copy_number_autosomal_contigs
    Array[String]? allosomal_contigs

    Int gcnv_qs_cutoff              # QS filtering cutoff

    # CNMops files
    File cnmops_blacklist

    Int? cnmops_large_min_size

    # QC files
    Int matrix_qc_distance

    RuntimeAttr? median_cov_runtime_attr        # Memory ignored, use median_cov_mem_gb_per_sample
    Float? median_cov_mem_gb_per_sample

    Int? evidence_merging_PE_size_mb
    Int? evidence_merging_SR_size_mb
    Int? evidence_merging_bincov_size_mb
    Int? evidence_merging_disk_overhead_gb            # Fixed extra disk
    RuntimeAttr? runtime_attr_shard_pe
    RuntimeAttr? runtime_attr_merge_pe
    RuntimeAttr? runtime_attr_shard_sr
    RuntimeAttr? runtime_attr_merge_sr
    RuntimeAttr? set_sample_runtime_attr
    RuntimeAttr? evidence_merging_bincov_runtime_attr # Disk space ignored, use evidence_merging_bincov_size_mb

    RuntimeAttr? cnmops_sample10_runtime_attr   # Memory ignored if cnmops_mem_gb_override_sample10 given
    RuntimeAttr? cnmops_sample3_runtime_attr    # Memory ignored if cnmops_mem_gb_override_sample3 given
    Float? cnmops_mem_gb_override_sample10
    Float? cnmops_mem_gb_override_sample3

    RuntimeAttr? add_sample_to_ped_runtime_attr
    RuntimeAttr? preprocess_calls_runtime_attr
    RuntimeAttr? depth_merge_set_runtime_attr
    RuntimeAttr? depth_merge_sample_runtime_attr
    RuntimeAttr? cnmops_ped_runtime_attr
    RuntimeAttr? cnmops_clean_runtime_attr
    RuntimeAttr? matrix_qc_pesrbaf_runtime_attr
    RuntimeAttr? matrix_qc_rd_runtime_attr

    RuntimeAttr? runtime_attr_ploidy
    RuntimeAttr? runtime_attr_case
    RuntimeAttr? runtime_attr_bundle
    RuntimeAttr? runtime_attr_postprocess
    RuntimeAttr? runtime_attr_explode

    ############################################################
    ## Module 01
    ############################################################

    # Depth merging parameters
    RuntimeAttr? runtime_attr_depth_merge_pre_01

    # Reference panel standardized caller VCFs
    Array[File] ref_std_manta_vcfs
    Array[File] ref_std_wham_vcfs
    File ref_panel_del_bed
    File ref_panel_dup_bed

    Int pesr_svsize
    Float pesr_frac
    String pesr_flags
    Int pesr_distance
    File pesr_blacklist
    String depth_flags
    Float depth_frac
    File? Sanders_2015_tarball
    File? Werling_2018_tarball
    File? Collins_2017_tarball

    RuntimeAttr? runtime_attr_pesr_cluster
    RuntimeAttr? runtime_attr_pesr_concat
    RuntimeAttr? runtime_attr_depth_cluster
    RuntimeAttr? runtime_attr_depth_concat
    RuntimeAttr? runtime_attr_depth_vcf
    RuntimeAttr? runtime_attr_rdtest_bed

    RuntimeAttr? runtime_attr_filter_vcf_by_id

    ############################################################
    ## Module 02/03
    ############################################################

    File rmsk
    File segdups
    Int tabix_retries = 5

    Int? min_large_pesr_call_size_for_filtering
    Float? min_large_pesr_depth_overlap_fraction

    RuntimeAttr? runtime_attr_filter_large_pesr
    RuntimeAttr? runtime_attr_srtest
    RuntimeAttr? runtime_attr_split_vcf
    RuntimeAttr? runtime_attr_merge_allo
    RuntimeAttr? runtime_attr_merge_stats
    RuntimeAttr? runtime_attr_rewritesrcoords

    RuntimeAttr? runtime_attr_merge_pesr_vcfs

    ############################################################
    ## Module 04
    ############################################################

    Int genotyping_n_per_split
    Int n_RD_genotype_bins  # number of RdTest bins

    File cutoffs

    File genotype_pesr_pesr_sepcutoff
    File genotype_pesr_depth_sepcutoff
    File genotype_depth_pesr_sepcutoff
    File genotype_depth_depth_sepcutoff

    File SR_metrics
    File PE_metrics

    File bin_exclude

    # Common
    RuntimeAttr? runtime_attr_split_vcf
    RuntimeAttr? runtime_attr_merge_counts
    RuntimeAttr? runtime_attr_split_variants
    RuntimeAttr? runtime_attr_make_subset_vcf
    RuntimeAttr? runtime_attr_rdtest_genotype
    RuntimeAttr? runtime_attr_add_genotypes
    RuntimeAttr? runtime_attr_concat_vcfs

    # Master
    RuntimeAttr? runtime_attr_add_batch
    RuntimeAttr? runtime_attr_index_vcf

    # PESR part 2
    RuntimeAttr? runtime_attr_count_pe
    RuntimeAttr? runtime_attr_genotype_pe
    RuntimeAttr? runtime_attr_count_sr
    RuntimeAttr? runtime_attr_genotype_sr
    RuntimeAttr? runtime_attr_integrate_gq
    RuntimeAttr? runtime_attr_integrate_pesr_gq
    RuntimeAttr? runtime_attr_triple_stream_cat

    # Depth part 2
    RuntimeAttr? runtime_attr_integrate_depth_gq
    RuntimeAttr? runtime_attr_concat_vcfs

    ############################################################
    ## Module 05_06
    ############################################################

    Float clean_vcf_min_sr_background_fail_batches
    Int clean_vcf_max_shards_per_chrom
    Int clean_vcf_min_variants_per_shard

    File cytobands

    File mei_bed
    File pe_blacklist
    File depth_blacklist
    File empty_file

    Int clean_vcf_max_shards_per_chrom_clean_vcf_step1
    Int clean_vcf_min_records_per_shard_clean_vcf_step1
    Int clean_vcf_samples_per_clean_vcf_step2_shard

    File ref_panel_outliers_excluded_list

    File? ref_panel_outlier_samples_list
    Int? clean_vcf_random_seed
    Boolean clean_vcf_include_external_benchmarking

    RuntimeAttr? runtime_override_update_sr_list
    RuntimeAttr? runtime_override_merge_pesr_depth
    RuntimeAttr? runtime_override_merge_pesr_depth
    RuntimeAttr? runtime_override_integrate_resolved_vcfs
    RuntimeAttr? runtime_override_rename_variants

    RuntimeAttr? runtime_override_clean_bothside_pass
    RuntimeAttr? runtime_override_clean_background_fail
    RuntimeAttr? runtime_override_merge_fam_file_list
    RuntimeAttr? runtime_override_make_cpx_cnv_input_file

    ############################################################
    ## Single sample filtering
    ############################################################

    Float? max_ref_panel_carrier_freq

    ############################################################
    ## QC
    ############################################################

    File qc_definitions

  }

  call m00a.Module00a as Module00a {
    input:
      bam_or_cram_files=[bam_or_cram_file],
      bam_or_cram_indexes=[bam_or_cram_index],
      requester_pays_crams=requester_pays_crams,
      samples=[sample_id],
      run_delly=false,
      batch=batch,
      primary_contigs_list=primary_contigs_list,
      reference_fasta=reference_fasta,
      reference_index=reference_index,
      reference_dict=reference_dict,
      reference_version=reference_version,
      bin_length=bin_length,
      coverage_blacklist=coverage_blacklist,
      mem_gb_for_preprocess_intervals=mem_gb_for_preprocess_intervals,
      manta_region_bed=manta_region_bed,
      manta_region_bed_index=manta_region_bed_index,
      manta_jobs_per_cpu=manta_jobs_per_cpu,
      manta_mem_gb_per_job=manta_mem_gb_per_job,
      wham_whitelist_bed_file=wham_whitelist_bed_file,
      sv_pipeline_docker=sv_pipeline_docker,
      sv_base_mini_docker=sv_base_mini_docker,
      manta_docker=manta_docker,
      wham_docker=wham_docker,
      gatk_docker=gatk_docker,
      genomes_in_the_cloud_docker=genomes_in_the_cloud_docker,
      samtools_cloud_docker=samtools_cloud_docker,
      runtime_attr_baf=runtime_attr_baf,
      runtime_attr_baf_gather=runtime_attr_baf_gather,
      runtime_attr_cram_to_bam=runtime_attr_cram_to_bam,
      runtime_attr_manta=runtime_attr_manta,
      runtime_attr_pesr=runtime_attr_pesr,
      runtime_attr_wham=runtime_attr_wham,
      runtime_attr_wham_whitelist=runtime_attr_wham_whitelist,
  }

  # to work around https://github.com/broadinstitute/cromwell/issues/4318
  Array[File]? emptyFileArray = []

  call m00b.Module00b as Module00b {
    input:
      batch=batch,
      samples=[sample_id],
      run_vcf_qc=run_vcf_qc,
      genome_file=genome_file,
      counts=select_first([Module00a.coverage_counts, emptyFileArray]),
      run_ploidy = false,
      wgd_scoring_mask=wgd_scoring_mask,
      sv_pipeline_docker=sv_pipeline_docker,
      sv_pipeline_qc_docker=sv_pipeline_qc_docker,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_qc=runtime_attr_qc,
      runtime_attr_qc_outlier=runtime_attr_qc_outlier,
      wgd_build_runtime_attr=wgd_build_runtime_attr,
      wgd_score_runtime_attr=wgd_score_runtime_attr
  }

  call m00c.Module00c as Module00c {
    input:
      batch=batch,
      samples=[sample_id],
      ref_panel_samples=ref_samples,
      run_matrix_qc=false,
      ped_file=ref_ped_file,
      genome_file=genome_file,
      primary_contigs_fai=primary_contigs_fai,
      counts=select_first([Module00a.coverage_counts, emptyFileArray]),
      ref_panel_bincov_matrix=ref_panel_bincov_matrix,
      PE_files=select_first([Module00a.pesr_disc, emptyFileArray]),
      cytoband=cytobands,
      mei_bed=mei_bed,
      ref_panel_PE_files=ref_pesr_disc_files,
      SR_files=select_first([Module00a.pesr_split, emptyFileArray]),
      ref_panel_SR_files=ref_pesr_split_files,
      inclusion_bed=inclusion_bed,
      contig_ploidy_model_tar = contig_ploidy_model_tar,
      gcnv_model_tars = gcnv_model_tars,
      gatk4_jar_override = gatk4_jar_override,
      run_ploidy = true,
      append_first_sample_to_ped = true,
      gcnv_p_alt = gcnv_p_alt,
      gcnv_cnv_coherence_length = gcnv_cnv_coherence_length,
      gcnv_max_copy_number = gcnv_max_copy_number,
      gcnv_mapping_error_rate = gcnv_mapping_error_rate,
      gcnv_sample_psi_scale = gcnv_sample_psi_scale,
      gcnv_depth_correction_tau = gcnv_depth_correction_tau,
      gcnv_copy_number_posterior_expectation_mode = gcnv_copy_number_posterior_expectation_mode,
      gcnv_active_class_padding_hybrid_mode = gcnv_active_class_padding_hybrid_mode,
      gcnv_learning_rate = gcnv_learning_rate,
      gcnv_adamax_beta_1 = gcnv_adamax_beta_1,
      gcnv_adamax_beta_2 = gcnv_adamax_beta_2,
      gcnv_log_emission_samples_per_round = gcnv_log_emission_samples_per_round,
      gcnv_log_emission_sampling_median_rel_error = gcnv_log_emission_sampling_median_rel_error,
      gcnv_log_emission_sampling_rounds = gcnv_log_emission_sampling_rounds,
      gcnv_max_advi_iter_first_epoch = gcnv_max_advi_iter_first_epoch,
      gcnv_max_advi_iter_subsequent_epochs = gcnv_max_advi_iter_subsequent_epochs,
      gcnv_min_training_epochs = gcnv_min_training_epochs,
      gcnv_max_training_epochs = gcnv_max_training_epochs,
      gcnv_initial_temperature = gcnv_initial_temperature,
      gcnv_num_thermal_advi_iters = gcnv_num_thermal_advi_iters,
      gcnv_convergence_snr_averaging_window = gcnv_convergence_snr_averaging_window,
      gcnv_convergence_snr_trigger_threshold = gcnv_convergence_snr_trigger_threshold,
      gcnv_convergence_snr_countdown_window = gcnv_convergence_snr_countdown_window,
      gcnv_max_calling_iters = gcnv_max_calling_iters,
      gcnv_caller_update_convergence_threshold = gcnv_caller_update_convergence_threshold,
      gcnv_caller_internal_admixing_rate = gcnv_caller_internal_admixing_rate,
      gcnv_caller_external_admixing_rate = gcnv_caller_external_admixing_rate,
      gcnv_disable_annealing = gcnv_disable_annealing,
      ref_copy_number_autosomal_contigs = ref_copy_number_autosomal_contigs,
      allosomal_contigs = allosomal_contigs,
      gcnv_qs_cutoff=gcnv_qs_cutoff,
      manta_vcfs=Module00a.manta_vcf,
      wham_vcfs=Module00a.wham_vcf,
      min_svsize=min_svsize,
      cnmops_chrom_file=autosome_file,
      cnmops_blacklist=cnmops_blacklist,
      cnmops_allo_file=allosome_file,
      cnmops_large_min_size=cnmops_large_min_size,
      matrix_qc_distance=matrix_qc_distance,
      sv_base_mini_docker=sv_base_mini_docker,
      sv_pipeline_docker=sv_pipeline_docker,
      sv_pipeline_qc_docker=sv_pipeline_qc_docker,
      linux_docker=linux_docker,
      cnmops_docker=cnmops_docker,
      gatk_docker = gatk_docker,
      condense_counts_docker = condense_counts_docker,
      median_cov_runtime_attr=median_cov_runtime_attr,
      median_cov_mem_gb_per_sample=median_cov_mem_gb_per_sample,
      evidence_merging_PE_size_mb=evidence_merging_PE_size_mb,
      evidence_merging_SR_size_mb=evidence_merging_SR_size_mb,
      evidence_merging_bincov_size_mb=evidence_merging_bincov_size_mb,
      evidence_merging_disk_overhead_gb=evidence_merging_disk_overhead_gb,
      set_sample_runtime_attr = set_sample_runtime_attr,
      runtime_attr_shard_pe = runtime_attr_shard_pe,
      runtime_attr_merge_pe = runtime_attr_merge_pe,
      runtime_attr_shard_sr = runtime_attr_shard_sr,
      runtime_attr_merge_sr = runtime_attr_merge_sr,
      evidence_merging_bincov_runtime_attr=evidence_merging_bincov_runtime_attr,
      cnmops_sample10_runtime_attr=cnmops_sample10_runtime_attr,
      cnmops_sample3_runtime_attr=cnmops_sample3_runtime_attr,
      cnmops_mem_gb_override_sample10=cnmops_mem_gb_override_sample10,
      cnmops_mem_gb_override_sample3=cnmops_mem_gb_override_sample3,
      preprocess_calls_runtime_attr=preprocess_calls_runtime_attr,
      depth_merge_set_runtime_attr=depth_merge_set_runtime_attr,
      depth_merge_sample_runtime_attr=depth_merge_sample_runtime_attr,
      cnmops_ped_runtime_attr=cnmops_ped_runtime_attr,
      cnmops_clean_runtime_attr=cnmops_clean_runtime_attr,
      matrix_qc_pesrbaf_runtime_attr=matrix_qc_pesrbaf_runtime_attr,
      matrix_qc_rd_runtime_attr=matrix_qc_rd_runtime_attr,
      ploidy_score_runtime_attr=ploidy_score_runtime_attr,
      ploidy_build_runtime_attr=ploidy_build_runtime_attr,
      add_sample_to_ped_runtime_attr=add_sample_to_ped_runtime_attr,
      runtime_attr_ploidy = runtime_attr_ploidy,
      runtime_attr_case = runtime_attr_case,
      runtime_attr_bundle = runtime_attr_bundle,
      runtime_attr_postprocess = runtime_attr_postprocess,
      runtime_attr_explode = runtime_attr_explode
  }

  File combined_ped_file = select_first([Module00c.combined_ped_file])

  # Merge calls with reference panel
  Array[File] merged_manta_vcfs_array = flatten([select_first([Module00c.std_manta_vcf]), ref_std_manta_vcfs])
  Array[File] merged_wham_vcfs_array = flatten([select_first([Module00c.std_wham_vcf]), ref_std_wham_vcfs])
  call dpn.MergeSet as MergeSetDel {
    input:
      beds=[Module00c.merged_dels, ref_panel_del_bed],
      svtype="DEL",
      batch=batch,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_depth_merge_pre_01
  }
  call dpn.MergeSet as MergeSetDup {
    input:
      beds=[Module00c.merged_dups, ref_panel_dup_bed],
      svtype="DUP",
      batch=batch,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_depth_merge_pre_01
  }

  call m01.Module01 as Module01 {
    input:
      manta_vcfs=merged_manta_vcfs_array,
      wham_vcfs=merged_wham_vcfs_array,
      del_bed=MergeSetDel.out,
      dup_bed=MergeSetDup.out,
      batch=batch,
      pesr_svsize=pesr_svsize,
      pesr_frac=pesr_frac,
      pesr_flags=pesr_flags,
      pesr_distance=pesr_distance,
      pesr_blacklist=pesr_blacklist,
      depth_blacklist=depth_blacklist,
      depth_blacklist_frac_max=0.5,
      depth_flags=depth_flags,
      depth_frac=depth_frac,
      contigs=primary_contigs_fai,
      sv_base_mini_docker=sv_base_mini_docker,
      sv_pipeline_docker=sv_pipeline_docker,
      runtime_attr_pesr_cluster=runtime_attr_pesr_cluster,
      runtime_attr_pesr_concat=runtime_attr_pesr_concat,
      runtime_attr_depth_cluster=runtime_attr_depth_cluster,
      runtime_attr_depth_concat=runtime_attr_depth_concat,
      runtime_attr_depth_vcf=runtime_attr_depth_vcf,
      runtime_attr_rdtest_bed=runtime_attr_rdtest_bed,
  }

  # Pull out clustered calls from this sample only
  call SingleSampleFiltering.FilterVcfBySampleGenotypeAndAddEvidenceAnnotation as FilterManta {
    input :
      vcf_gz=select_first([Module01.manta_vcf]),
      sample_id=sample_id,
      evidence="RD,PE,SR",
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_filter_vcf_by_id
  }
  call SingleSampleFiltering.FilterVcfBySampleGenotypeAndAddEvidenceAnnotation as FilterWham {
    input :
      vcf_gz=select_first([Module01.wham_vcf]),
      sample_id=sample_id,
      evidence="RD,PE,SR",
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_filter_vcf_by_id
  }
  call SingleSampleFiltering.FilterVcfBySampleGenotypeAndAddEvidenceAnnotation as FilterDepth {
    input :
      vcf_gz=Module01.depth_vcf,
      sample_id=sample_id,
      evidence="RD",
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_filter_vcf_by_id
  }

  call m03.MergePesrVcfs as MergePesrVcfs {
    input:
      manta_vcf=FilterManta.out,
      wham_vcf=FilterWham.out,
      batch=batch,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_attr_merge_pesr_vcfs
  }

  call SingleSampleFiltering.FilterLargePESRCallsWithoutRawDepthSupport as FilterLargePESRCallsWithoutRawDepthSupport {
    input:
      pesr_vcf=MergePesrVcfs.merged_pesr_vcf,
      raw_dels=Module00c.merged_dels,
      raw_dups=Module00c.merged_dups,
      min_large_pesr_call_size_for_filtering=min_large_pesr_call_size_for_filtering,
      min_large_pesr_depth_overlap_fraction=min_large_pesr_depth_overlap_fraction,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr_override=runtime_attr_filter_large_pesr
  }

  call m02.GetSampleLists as SamplesList {
    input:
      ped_file = combined_ped_file,
      samples = flatten([[sample_id], read_lines(ref_panel_outliers_excluded_list)]),
      sv_base_docker = sv_base_docker
  }

  call SRTest.SRTest as SRTest {
    input:
      splitfile = Module00c.merged_SR,
      medianfile = Module00c.median_cov,
      ped_file = combined_ped_file,
      vcf = FilterLargePESRCallsWithoutRawDepthSupport.out,
      autosome_contigs = autosome_file,
      split_size = genotyping_n_per_split,
      algorithm = "PESR",
      allosome_contigs = allosome_file,
      batch = batch,
      samples = SamplesList.samples_file,
      male_samples = SamplesList.male_samples,
      female_samples = SamplesList.female_samples,
      run_common = false,
      tabix_retries = tabix_retries,
      sv_base_mini_docker = sv_base_mini_docker,
      linux_docker = linux_docker,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr_srtest = runtime_attr_srtest,
      runtime_attr_split_vcf = runtime_attr_split_vcf,
      runtime_attr_merge_allo = runtime_attr_merge_allo,
      runtime_attr_merge_stats = runtime_attr_merge_stats
  }

  call m02.AggregateTests as AggregateTests {
    input:
      vcf=FilterLargePESRCallsWithoutRawDepthSupport.out,
      srtest=SRTest.srtest,
      rmsk=rmsk,
      segdups=segdups,
      sv_pipeline_docker=sv_pipeline_docker
  }

  call SingleSampleFiltering.RewriteSRCoords as RewriteSRCoords {
    input:
      vcf = FilterLargePESRCallsWithoutRawDepthSupport.out,
      metrics = AggregateTests.metrics,
      cutoffs = cutoffs,
      prefix = batch,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr_override = runtime_attr_rewritesrcoords
  }

  call m04.Module04 as Module04 {
    input:
      batch_pesr_vcf=RewriteSRCoords.annotated_vcf,
      batch_depth_vcf=FilterDepth.out,
      cohort_pesr_vcf=RewriteSRCoords.annotated_vcf,
      cohort_depth_vcf=FilterDepth.out,
      batch=batch,
      n_per_split=genotyping_n_per_split,
      medianfile=Module00c.median_cov,
      coveragefile=Module00c.merged_bincov,
      discfile=Module00c.merged_PE,
      splitfile=Module00c.merged_SR,
      samples=flatten([[sample_id], ref_samples]),
      famfile=combined_ped_file,
      n_RD_genotype_bins=n_RD_genotype_bins,
      genotype_pesr_pesr_sepcutoff=genotype_pesr_pesr_sepcutoff,
      genotype_pesr_depth_sepcutoff=genotype_pesr_depth_sepcutoff,
      genotype_depth_pesr_sepcutoff=genotype_depth_pesr_sepcutoff,
      genotype_depth_depth_sepcutoff=genotype_depth_depth_sepcutoff,
      SR_metrics=SR_metrics,
      PE_metrics=PE_metrics,
      bin_exclude=bin_exclude,
      sv_base_mini_docker=sv_base_mini_docker,
      sv_pipeline_docker=sv_pipeline_docker,
      sv_pipeline_rdtest_docker=sv_pipeline_rdtest_docker,
      linux_docker=linux_docker,
      runtime_attr_split_vcf=runtime_attr_split_vcf,
      runtime_attr_merge_counts=runtime_attr_merge_counts,
      runtime_attr_split_variants=runtime_attr_split_variants,
      runtime_attr_make_subset_vcf=runtime_attr_make_subset_vcf,
      runtime_attr_rdtest_genotype=runtime_attr_rdtest_genotype,
      runtime_attr_add_genotypes=runtime_attr_add_genotypes,
      runtime_attr_concat_vcfs=runtime_attr_concat_vcfs,
      runtime_attr_add_batch=runtime_attr_add_batch,
      runtime_attr_index_vcf=runtime_attr_index_vcf,
      runtime_attr_count_pe=runtime_attr_count_pe,
      runtime_attr_genotype_pe=runtime_attr_genotype_pe,
      runtime_attr_count_sr=runtime_attr_count_sr,
      runtime_attr_genotype_sr=runtime_attr_genotype_sr,
      runtime_attr_integrate_gq=runtime_attr_integrate_gq,
      runtime_attr_integrate_pesr_gq=runtime_attr_integrate_pesr_gq,
      runtime_attr_triple_stream_cat=runtime_attr_triple_stream_cat,
      runtime_attr_integrate_depth_gq=runtime_attr_integrate_depth_gq,
      runtime_attr_concat_vcfs=runtime_attr_concat_vcfs
  }

  call SingleSampleFiltering.ConvertCNVsWithoutDepthSupportToBNDs as ConvertCNVsWithoutDepthSupportToBNDs {
    input:
      genotyped_pesr_vcf=Module04.genotyped_pesr_vcf,
      allosome_file=allosome_file,
      merged_famfile=combined_ped_file,
      case_sample=sample_id,
      sv_pipeline_docker=sv_pipeline_docker
  }

  call m0506.Module05_06 as Module0506 {
    input:
      raw_sr_bothside_pass_files=[Module04.sr_bothside_pass],
      raw_sr_background_fail_files=[Module04.sr_background_fail],
      min_sr_background_fail_batches=clean_vcf_min_sr_background_fail_batches,
      ped_files=[combined_ped_file],
      pesr_vcfs=[ConvertCNVsWithoutDepthSupportToBNDs.out_vcf],
      depth_vcfs=[Module04.genotyped_depth_vcf],
      contig_list=primary_contigs_fai,
      ref_build=ref_build,

      max_shards_per_chrom=clean_vcf_max_shards_per_chrom,
      min_variants_per_shard=clean_vcf_min_variants_per_shard,
      cytobands=cytobands,

      bin_exclude=bin_exclude,

      disc_files=[Module00c.merged_PE],
      bincov_files=[Module00c.merged_bincov],

      mei_bed=mei_bed,
      pe_blacklist=pe_blacklist,
      depth_blacklist=depth_blacklist,
      empty_file=empty_file,

      cohort_name=batch,
      sanders_2015_tarball=Sanders_2015_tarball,
      collins_2017_tarball=Collins_2017_tarball,
      werling_2018_tarball=Werling_2018_tarball,

      rf_cutoff_files=[cutoffs],
      batches=[batch],
      depth_gt_rd_sep_files=[genotype_depth_depth_sepcutoff],
      median_coverage_files=[Module00c.median_cov],
      samplelist_files=[SamplesList.samples_file],

      max_shards_per_chrom_clean_vcf_step1=clean_vcf_max_shards_per_chrom_clean_vcf_step1,
      min_records_per_shard_clean_vcf_step1=clean_vcf_min_records_per_shard_clean_vcf_step1,
      samples_per_clean_vcf_step2_shard=clean_vcf_samples_per_clean_vcf_step2_shard,

      outlier_samples_list=ref_panel_outlier_samples_list,
      random_seed=clean_vcf_random_seed,
      include_external_benchmarking=clean_vcf_include_external_benchmarking,

      sv_pipeline_docker=sv_pipeline_docker,
      sv_pipeline_rdtest_docker=sv_pipeline_rdtest_docker,
      sv_pipeline_qc_docker=sv_pipeline_qc_docker,
      sv_base_mini_docker=sv_base_mini_docker,

      runtime_override_update_sr_list=runtime_override_update_sr_list,
      runtime_override_merge_pesr_depth=runtime_override_merge_pesr_depth,
      runtime_override_breakpoint_overlap_filter=runtime_override_merge_pesr_depth,
      runtime_override_integrate_resolved_vcfs=runtime_override_integrate_resolved_vcfs,
      runtime_override_rename_variants=runtime_override_rename_variants,

      runtime_override_clean_bothside_pass=runtime_override_clean_bothside_pass,
      runtime_override_clean_background_fail=runtime_override_clean_background_fail,
      runtime_override_merge_fam_file_list=runtime_override_merge_fam_file_list,
      runtime_override_make_cpx_cnv_input_file=runtime_override_make_cpx_cnv_input_file

  }

  call SingleSampleFiltering.FilterVcfForShortDepthCalls as FilterVcfDepthLt5kb {
    input:
      vcf_gz=Module0506.cleaned_vcf,
      min_length=5000,
      filter_name="DEPTH_LT_5KB",
      sv_base_mini_docker=sv_base_mini_docker
  }

  call SingleSampleFiltering.GetUniqueNonGenotypedDepthCalls as GetUniqueNonGenotypedDepthCalls {
    input:
      vcf_gz=Module0506.final_04b_vcf,
      sample_id=sample_id,
      ref_panel_dels=ref_panel_del_bed,
      ref_panel_dups=ref_panel_dup_bed,
      sv_base_mini_docker=sv_base_mini_docker
  }

  call SingleSampleFiltering.FilterVcfForCaseSampleGenotype as FilterVcfForCaseSampleGenotype {
    input:
      vcf_gz=FilterVcfDepthLt5kb.out,
      sample_id=sample_id,
      sv_base_mini_docker=sv_base_mini_docker
  }

  call SingleSampleFiltering.FilterVcfWithReferencePanelCalls as FilterVcfWithReferencePanelCalls {
    input:
      single_sample_vcf=FilterVcfForCaseSampleGenotype.out,
      cohort_vcf=ref_panel_vcf,
      case_sample_id=sample_id,
      max_ref_panel_carrier_freq=max_ref_panel_carrier_freq,
      sv_pipeline_docker=sv_pipeline_docker
  }

  call SingleSampleFiltering.ResetFilter as ResetHighSRBackgroundFilter {
    input:
      single_sample_vcf=FilterVcfWithReferencePanelCalls.out,
      single_sample_vcf_idx=FilterVcfWithReferencePanelCalls.out_idx,
      filter_to_reset="HIGH_SR_BACKGROUND",
      info_header_line='##INFO=<ID=HIGH_SR_BACKGROUND,Number=0,Type=Flag,Description="Sites with high split read background">',
      sv_base_mini_docker=sv_base_mini_docker
  }

  call SingleSampleFiltering.ResetFilter as ResetBothsidesSupportFilter {
      input:
        single_sample_vcf=ResetHighSRBackgroundFilter.out,
        single_sample_vcf_idx=ResetHighSRBackgroundFilter.out_idx,
        filter_to_reset="BOTHSIDES_SUPPORT",
        info_header_line='##INFO=<ID=BOTHSIDES_SUPPORT,Number=0,Type=Flag,Description="Sites with split read support at both breakpoints">',
        sv_base_mini_docker=sv_base_mini_docker
    }

  call SingleSampleFiltering.FinalVCFCleanup as FinalVCFCleanup {
    input:
      single_sample_vcf=ResetBothsidesSupportFilter.out,
      single_sample_vcf_idx=ResetBothsidesSupportFilter.out_idx,
      ref_fasta=reference_fasta,
      ref_fasta_idx=reference_index,
      sv_pipeline_docker=sv_pipeline_docker
  }

  call SingleSampleMetrics.SingleSampleMetrics {
    input:
      name = batch,
      ref_samples = ref_samples,
      case_sample = sample_id,
      wgd_scores = Module00b.WGD_scores,
      sample_pe = select_first([Module00a.pesr_disc])[0],
      sample_sr = select_first([Module00a.pesr_split])[0],
      sample_counts = select_first([Module00a.coverage_counts])[0],
      cleaned_vcf = Module0506.cleaned_vcf,
      final_vcf = FinalVCFCleanup.out,
      genotyped_pesr_vcf = ConvertCNVsWithoutDepthSupportToBNDs.out_vcf,
      genotyped_depth_vcf = Module04.genotyped_depth_vcf,
      non_genotyped_unique_depth_calls_vcf = GetUniqueNonGenotypedDepthCalls.out,
      contig_list = primary_contigs_list,
      linux_docker = linux_docker,
      sv_pipeline_base_docker = sv_pipeline_base_docker
  }

  call utils.RunQC as SingleSampleQC {
    input:
      name = batch,
      metrics = SingleSampleMetrics.metrics_file,
      qc_definitions = qc_definitions,
      sv_pipeline_base_docker = sv_pipeline_base_docker
  }

  output {
    File final_vcf = FinalVCFCleanup.out
    File final_vcf_idx = FinalVCFCleanup.out_idx
    File ploidy_matrix = select_first([Module00c.ploidy_matrix])
    File ploidy_plots = select_first([Module00c.ploidy_plots])
    File metrics_file = SingleSampleMetrics.metrics_file
    File qc_file = SingleSampleQC.out

    # These files contain any depth based calls made in the case sample that did not pass genotyping
    # in the case sample and do not match a depth-based call from the reference panel.
    File non_genotyped_unique_depth_calls = GetUniqueNonGenotypedDepthCalls.out
    File non_genotyped_unique_depth_calls_idx = GetUniqueNonGenotypedDepthCalls.out_idx
  }

  meta {
    author: "Christopher Whelan"
    email: "cwhelan@broadinstitute.org"
    description: "GATK-SV pipeline for single sample WGS structural variation calling. This is a prototype version under development that is not supported."
  }
}
