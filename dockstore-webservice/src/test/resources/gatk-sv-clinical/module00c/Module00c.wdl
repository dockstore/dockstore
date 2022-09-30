version 1.0

##########################################################################################

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

import "Structs.wdl"
import "BatchEvidenceMerging.wdl" as bem
import "CNMOPS.wdl" as cnmops
import "CollectCoverage.wdl" as cov
import "DepthPreprocessing.wdl" as dpn
import "MakeBincovMatrix.wdl" as mbm
import "MatrixQC.wdl" as mqc
import "MedianCov.wdl" as mc
import "PESRPreprocessing.wdl" as pp
import "GermlineCNVCase.wdl" as gcnv
import "PloidyEstimation.wdl" as pe

# Batch-level workflow:
#   - Merge sample evidence data into a single batch
#   - Run cnMOPS
#   - Run gCNV
#   - Run MedianCoverage

workflow Module00c {
  input {
    # Batch info
    String batch
    Array[String]+ samples
    Array[String]? ref_panel_samples

    # Optional QC tasks
    Boolean run_matrix_qc

    # Global files
    File ped_file
    File genome_file
    File primary_contigs_fai            # .fai file of whitelisted contigs

    # PE/SR/BAF/bincov files
    Array[File] counts
    File? ref_panel_bincov_matrix
    Array[File]? BAF_files         # Required for MatrixQC
    Array[File]+ PE_files
    Array[File]? ref_panel_PE_files
    Array[File]+ SR_files
    Array[File]? ref_panel_SR_files
    File inclusion_bed

    # Condense read counts
    Int? condense_num_bins
    Int? condense_bin_size

    # gCNV inputs
    File contig_ploidy_model_tar
    Array[File] gcnv_model_tars

    File? gatk4_jar_override
    Int? preemptible_attempts

    Int? mem_gb_for_determine_germline_contig_ploidy
    Int? cpu_for_determine_germline_contig_ploidy
    Int? disk_for_determine_germline_contig_ploidy
    Float? gcnv_p_alt
    Float? gcnv_cnv_coherence_length
    Int? gcnv_max_copy_number
    Int? mem_gb_for_germline_cnv_caller
    Int? cpu_for_germline_cnv_caller
    Int? disk_for_germline_cnv_caller

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

    Float? ploidy_sample_psi_scale
    Int? postprocessing_mem_gb
    Int ref_copy_number_autosomal_contigs
    Array[String]? allosomal_contigs

    Boolean run_ploidy = false

    # Option to add first sample to the ped file (for single sample mode); run_ploidy must be true
    Boolean append_first_sample_to_ped = false

    Int gcnv_qs_cutoff              # QS filtering cutoff

    # SV tool calls
    Array[File]+? manta_vcfs        # Manta VCF
    Array[File]+? delly_vcfs        # Delly VCF
    Array[File]+? melt_vcfs         # Melt VCF
    Array[File]+? wham_vcfs         # Wham VCF
    Int min_svsize                  # Minimum SV length to include

    # CNMops files
    File cnmops_chrom_file
    File cnmops_blacklist
    File cnmops_allo_file

    # QC files
    Int matrix_qc_distance

    # Runtime parameters
    String sv_mini_docker
    String sv_pipeline_docker
    String sv_pipeline_qc_docker
    String linux_docker
    String condense_counts_docker
    String gatk_docker
    String cnmops_docker

    RuntimeAttr? median_cov_runtime_attr        # Memory ignored, use median_cov_mem_gb_per_sample
    Float? median_cov_mem_gb_per_sample

    Int? evidence_merging_BAF_size_mb
    Int? evidence_merging_PE_size_mb
    Int? evidence_merging_SR_size_mb
    Int? evidence_merging_bincov_size_mb
    Int? evidence_merging_disk_overhead_gb            # Fixed extra disk
    RuntimeAttr? evidence_merging_pesr_runtime_attr   # Disk space ignored, use evidence_merging_<BAF/PE/SR>_size_mb
    RuntimeAttr? evidence_merging_bincov_runtime_attr # Disk space ignored, use evidence_merging_bincov_size_mb

    RuntimeAttr? cnmops_sample10_runtime_attr   # Memory ignored if cnmops_mem_gb_override_sample10 given
    RuntimeAttr? cnmops_sample3_runtime_attr    # Memory ignored if cnmops_mem_gb_override_sample3 given
    Float? cnmops_mem_gb_override_sample10
    Float? cnmops_mem_gb_override_sample3

    RuntimeAttr? ploidy_score_runtime_attr
    RuntimeAttr? ploidy_build_runtime_attr
    RuntimeAttr? add_sample_to_ped_runtime_attr
    RuntimeAttr? condense_counts_runtime_attr
    RuntimeAttr? preprocess_calls_runtime_attr
    RuntimeAttr? depth_merge_set_runtime_attr
    RuntimeAttr? depth_merge_sample_runtime_attr
    RuntimeAttr? cnmops_ped_runtime_attr
    RuntimeAttr? cnmops_clean_runtime_attr
    RuntimeAttr? matrix_qc_pesrbaf_runtime_attr
    RuntimeAttr? matrix_qc_rd_runtime_attr
  }

  Array[String] all_samples = flatten(select_all([samples, ref_panel_samples]))
  Array[File] all_PE_files = flatten(select_all([PE_files, ref_panel_PE_files]))
  Array[File] all_SR_files = flatten(select_all([SR_files, ref_panel_SR_files]))

  call mbm.MakeBincovMatrix as MakeBincovMatrix {
    input:
      samples = samples,
      count_files = counts,
      bincov_matrix = ref_panel_bincov_matrix,
      bincov_matrix_samples = ref_panel_samples,
      batch = batch,
      disk_overhead_gb = evidence_merging_disk_overhead_gb,
      bincov_size_mb = evidence_merging_bincov_size_mb,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = evidence_merging_bincov_runtime_attr
  }

  if (run_ploidy) {
    call pe.Ploidy as Ploidy {
      input:
        bincov_matrix = MakeBincovMatrix.merged_bincov,
        batch = batch,
        sv_mini_docker = sv_mini_docker,
        sv_pipeline_qc_docker = sv_pipeline_qc_docker,
        runtime_attr_score = ploidy_score_runtime_attr,
        runtime_attr_build = ploidy_build_runtime_attr
    }
  }

  if (append_first_sample_to_ped) {
    call AddCaseSampleToPed {
      input:
        ref_ped_file = ped_file,
        ploidy_plots = select_first([Ploidy.ploidy_plots]),
        sample_id = samples[0],
        sv_mini_docker = sv_mini_docker,
        runtime_attr_override = add_sample_to_ped_runtime_attr
    }
  }

  call bem.EvidenceMerging as EvidenceMerging {
    input:
      BAF_files = BAF_files,
      PE_files = all_PE_files,
      SR_files = all_SR_files,
      inclusion_bed = inclusion_bed,
      batch = batch,
      BAF_size_mb = evidence_merging_BAF_size_mb,
      PE_size_mb = evidence_merging_PE_size_mb,
      SR_size_mb = evidence_merging_SR_size_mb,
      disk_overhead_gb = evidence_merging_disk_overhead_gb,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_pesr = evidence_merging_pesr_runtime_attr
  }

  call cnmops.CNMOPS as CNMOPS {
    input:
      batch = batch,
      samples = all_samples,
      bincov_matrix = MakeBincovMatrix.merged_bincov,
      bincov_matrix_index = MakeBincovMatrix.merged_bincov_idx,
      chrom_file = cnmops_chrom_file,
      ped_file = select_first([AddCaseSampleToPed.combined_ped_file, ped_file]),
      blacklist = cnmops_blacklist,
      allo_file = cnmops_allo_file,
      mem_gb_override_sample10 = cnmops_mem_gb_override_sample10,
      mem_gb_override_sample3 = cnmops_mem_gb_override_sample3,
      linux_docker = linux_docker,
      sv_pipeline_docker = sv_pipeline_docker,
      cnmops_docker = cnmops_docker,
      runtime_attr_sample10 = cnmops_sample10_runtime_attr,
      runtime_attr_sample3 = cnmops_sample3_runtime_attr,
      runtime_attr_ped = cnmops_ped_runtime_attr,
      runtime_attr_clean = cnmops_clean_runtime_attr
  }

  scatter (i in range(length(samples))) {
    call cov.CondenseReadCounts as CondenseReadCounts {
      input:
        counts = counts[i],
        sample = samples[i],
        num_bins = condense_num_bins,
        expected_bin_size = condense_bin_size,
        condense_counts_docker = condense_counts_docker,
        runtime_attr_override=condense_counts_runtime_attr
    }
  }

  call gcnv.CNVGermlineCaseWorkflow as gCNVCase {
    input:
      counts = CondenseReadCounts.out,
      count_entity_ids = samples,
      contig_ploidy_model_tar = contig_ploidy_model_tar,
      gcnv_model_tars = gcnv_model_tars,
      gatk_docker = gatk_docker,
      linux_docker = linux_docker,
      gatk4_jar_override = gatk4_jar_override,
      preemptible_attempts = preemptible_attempts,
      mem_gb_for_determine_germline_contig_ploidy = mem_gb_for_determine_germline_contig_ploidy,
      cpu_for_determine_germline_contig_ploidy = cpu_for_determine_germline_contig_ploidy,
      disk_for_determine_germline_contig_ploidy = disk_for_determine_germline_contig_ploidy,
      gcnv_p_alt = gcnv_p_alt,
      gcnv_cnv_coherence_length = gcnv_cnv_coherence_length,
      gcnv_max_copy_number = gcnv_max_copy_number,
      mem_gb_for_germline_cnv_caller = mem_gb_for_germline_cnv_caller,
      cpu_for_germline_cnv_caller = cpu_for_germline_cnv_caller,
      disk_for_germline_cnv_caller = disk_for_germline_cnv_caller,
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
      postprocessing_mem_gb = postprocessing_mem_gb,
      ref_copy_number_autosomal_contigs = ref_copy_number_autosomal_contigs,
      allosomal_contigs = allosomal_contigs
  }

  call dpn.MergeDepth as MergeDepth {
    input:
      samples = samples,
      genotyped_segments_vcfs = gCNVCase.genotyped_segments_vcf,
      contig_ploidy_calls = gCNVCase.sample_contig_ploidy_calls_tars,
      gcnv_qs_cutoff = gcnv_qs_cutoff,
      std_cnmops_del = CNMOPS.Del,
      std_cnmops_dup = CNMOPS.Dup,
      batch = batch,
      sv_pipeline_docker = sv_pipeline_docker,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_merge_sample = depth_merge_sample_runtime_attr,
      runtime_attr_merge_set = depth_merge_set_runtime_attr
  }

  Float median_cov_mem_gb = select_first([median_cov_mem_gb_per_sample, 0.5]) * length(all_samples)
  call mc.MedianCov as MedianCov {
    input:
      bincov_matrix = MakeBincovMatrix.merged_bincov,
      cohort_id = batch,
      sv_pipeline_qc_docker = sv_pipeline_qc_docker,
      runtime_attr = median_cov_runtime_attr,
      mem_gb_override = median_cov_mem_gb
  }

  call pp.PreprocessPESR as PreprocessPESR {
    input:
      samples = samples,
      manta_vcfs = manta_vcfs,
      delly_vcfs = delly_vcfs,
      melt_vcfs = melt_vcfs,
      wham_vcfs = wham_vcfs,
      contigs = primary_contigs_fai,
      min_svsize = min_svsize,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr = preprocess_calls_runtime_attr
  }

  if (run_matrix_qc) {
    call mqc.MatrixQC as MatrixQC {
      input: 
        distance = matrix_qc_distance,
        genome_file = genome_file,
        batch = batch,
        PE_file = EvidenceMerging.merged_PE,
        PE_idx = EvidenceMerging.merged_PE_idx,
        BAF_file = select_first([EvidenceMerging.merged_BAF]),
        BAF_idx = select_first([EvidenceMerging.merged_BAF_idx]),
        RD_file = MakeBincovMatrix.merged_bincov,
        RD_idx = MakeBincovMatrix.merged_bincov_idx,
        SR_file = EvidenceMerging.merged_SR,
        SR_idx = EvidenceMerging.merged_SR_idx,
        sv_pipeline_docker = sv_pipeline_docker,
        runtime_attr_pesrbaf = matrix_qc_pesrbaf_runtime_attr,
        runtime_attr_rd = matrix_qc_rd_runtime_attr
    }
  }

  output {
    File? merged_BAF = EvidenceMerging.merged_BAF
    File? merged_BAF_index = EvidenceMerging.merged_BAF_idx
    File merged_SR = EvidenceMerging.merged_SR
    File merged_SR_index = EvidenceMerging.merged_SR_idx
    File merged_PE = EvidenceMerging.merged_PE
    File merged_PE_index = EvidenceMerging.merged_PE_idx
    File merged_bincov = MakeBincovMatrix.merged_bincov
    File merged_bincov_index = MakeBincovMatrix.merged_bincov_idx

    File? ploidy_matrix = Ploidy.ploidy_matrix
    File? ploidy_plots = Ploidy.ploidy_plots

    File? combined_ped_file = AddCaseSampleToPed.combined_ped_file

    File merged_dels = MergeDepth.del
    File merged_dups = MergeDepth.dup

    File cnmops_del = CNMOPS.Del
    File cnmops_del_index = CNMOPS.Del_idx
    File cnmops_dup = CNMOPS.Dup
    File cnmops_dup_index = CNMOPS.Dup_idx

    File median_cov = MedianCov.medianCov

    Array[File]+? std_manta_vcf = PreprocessPESR.std_manta_vcf
    Array[File]+? std_delly_vcf = PreprocessPESR.std_delly_vcf
    Array[File]+? std_melt_vcf = PreprocessPESR.std_melt_vcf
    Array[File]+? std_wham_vcf = PreprocessPESR.std_wham_vcf

    File? PE_stats = MatrixQC.PE_stats
    File? RD_stats = MatrixQC.RD_stats
    File? SR_stats = MatrixQC.SR_stats
    File? BAF_stats = MatrixQC.BAF_stats
    File? Matrix_QC_plot = MatrixQC.QC_plot
  }
}

task AddCaseSampleToPed {
  input {
    File ref_ped_file
    File ploidy_plots
    String sample_id
    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 2,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File combined_ped_file = "combined_ped_file.ped"
  }

  command <<<

    set -euo pipefail

    tar xzf ~{ploidy_plots} -C .
    RECORD=$(gunzip -c ploidy_est/sample_sex_assignments.txt.gz | { grep -w "^~{sample_id}" || true; })
    if [ -z "$RECORD" ]; then
      >&2 echo "Error: Sample ~{sample_id} not found in ploidy calls"
      exit 1
    fi
    SEX=$(echo "$RECORD" | cut -f2)

    awk -v sample=~{sample_id} '$2 == sample { print "ERROR: A sample with the name "sample" is already present in the ped file." > "/dev/stderr"; exit 1; }' < ~{ref_ped_file}
    awk -v sample=~{sample_id} -v sex=$SEX '{print} END {OFS="\t"; print "case_sample",sample,"0","0",sex,"1" }' < ~{ref_ped_file} > combined_ped_file.ped
  >>>

  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}
