##########################################################################################

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

version 1.0

import "Structs.wdl"
import "BAFFromGVCFs.wdl" as baf
import "BAFFromShardedVCF.wdl" as sbaf
import "CollectCoverage.wdl" as cov
import "CramToBam.wdl" as ctb
import "Delly.wdl" as delly
import "Manta.wdl" as manta
import "PESRCollection.wdl" as pesr
import "Whamg.wdl" as wham

# Runs selected tools on BAM/CRAM files

workflow Module00a {
  input {

    # Required for all except BAF
    Array[File]? bam_or_cram_files
    Array[File]? bam_or_cram_indexes

    # Use only for crams in requester pays buckets
    Boolean requester_pays_crams = false

    # BAF Option #1 (provide all)
    # From single-sample gVCFS
    Array[File]? gvcfs
    Array[File]? gvcf_indexes
    File? unpadded_intervals_file
    File? dbsnp_vcf

    # BAF Option #2
    # From multi-sample VCFs (sharded by position)
    Array[File]? vcfs
    File? vcf_header # Required only if VCFs are unheadered

    ############ MUST MATCH SAMPLE IDS IN BAM HEADERS! ############
    Array[String] samples

    # Caller flags
    Boolean run_delly = false
    Boolean run_manta = true
    Boolean run_wham = true
    Boolean collect_coverage = true
    Boolean collect_pesr = true

    # If true, any intermediate BAM files will be deleted after the algorithms have completed.
    # NOTE: If the workflow (ie any algorithm) fails, the bam will NOT be deleted.
    Boolean delete_intermediate_bam = false

    # Common parameters
    String batch
    File primary_contigs_list
    File reference_fasta
    File reference_index    # Index (.fai), must be in same dir as fasta
    File reference_dict     # Dictionary (.dict), must be in same dir as fasta
    String? reference_version   # Either "38" or "19"

    # Coverage collection inputs
    Int? bin_length
    File? coverage_blacklist
    Float? mem_gb_for_preprocess_intervals
    Float? mem_gb_for_collect_counts
    Int? disk_space_gb_for_collect_counts

    # Delly inputs
    File? delly_blacklist_intervals_file  # Required if run_delly True
    Array[String]? delly_sv_types

    # Manta inputs
    File manta_region_bed
    File? manta_region_bed_index
    Float? manta_jobs_per_cpu
    Int? manta_mem_gb_per_job

    # Wham inputs
    File wham_whitelist_bed_file

    # Docker
    String sv_pipeline_docker
    String sv_base_mini_docker
    String samtools_cloud_docker
    String? delly_docker
    String? manta_docker
    String? wham_docker
    String gatk_docker
    String genomes_in_the_cloud_docker

    # Runtime configuration overrides
    RuntimeAttr? runtime_attr_baf
    RuntimeAttr? runtime_attr_baf_gather
    RuntimeAttr? runtime_attr_merge_vcfs
    RuntimeAttr? runtime_attr_baf_sample
    RuntimeAttr? runtime_attr_cram_to_bam
    RuntimeAttr? runtime_attr_delly
    RuntimeAttr? runtime_attr_delly_gather
    RuntimeAttr? runtime_attr_manta
    RuntimeAttr? runtime_attr_pesr
    RuntimeAttr? runtime_attr_wham
    RuntimeAttr? runtime_attr_wham_whitelist

    # Never assign these values! (workaround until None type is implemented)
    Float? NONE_FLOAT_
    Int? NONE_INT_
    File? NONE_FILE_
  }

  if (defined(vcfs)) {
    Array[File] select_vcfs = select_first([vcfs])
    call sbaf.BAFFromShardedVCF as BAFFromShardedVCF {
      input:
        vcfs = select_vcfs,
        vcf_header = vcf_header,
        samples = samples,
        batch = batch,
        sv_base_mini_docker = sv_base_mini_docker,
        sv_pipeline_docker = sv_pipeline_docker,
        runtime_attr_baf_gen = runtime_attr_baf,
        runtime_attr_gather = runtime_attr_baf_gather,
        runtime_attr_sample = runtime_attr_baf_sample
    }
  }

  if (!defined(vcfs) && defined(gvcfs)) {
    Array[File] select_gvcfs = select_first([gvcfs])
    scatter (i in range(length(select_gvcfs))) {
      File select_gvcf_indexes = if defined(gvcf_indexes) then select_first([gvcf_indexes])[i] else select_gvcfs[i] + ".tbi"
    }
    call baf.BAFFromGVCFs {
      input:
        gvcfs = select_gvcfs,
        gvcf_indexes = select_gvcf_indexes,
        unpadded_intervals_file = select_first([unpadded_intervals_file]),
        dbsnp_vcf = select_first([dbsnp_vcf]),
        samples = samples,
        chrom_file = primary_contigs_list,
        ref_fasta = reference_fasta,
        ref_fasta_index = reference_index,
        ref_dict = reference_dict,
        batch = batch,
        gatk_docker = gatk_docker,
        sv_base_mini_docker = sv_base_mini_docker,
        sv_pipeline_docker = sv_pipeline_docker,
        runtime_attr_merge_vcfs = runtime_attr_merge_vcfs,
        runtime_attr_baf_gen = runtime_attr_baf,
        runtime_attr_gather = runtime_attr_baf_gather,
        runtime_attr_sample = runtime_attr_baf_sample
    }
  }

  Boolean bam_needed = run_delly || run_manta || run_wham || collect_coverage || collect_pesr
  if (bam_needed) {
    scatter (i in range(length(samples))) {
      File bam_or_cram_file = select_first([bam_or_cram_files])[i]
      Boolean is_bam = basename(bam_or_cram_file, ".bam") + ".bam" == basename(bam_or_cram_file)
      String index_ext = if is_bam then ".bai" else ".crai"
      File bam_or_cram_index = if defined(bam_or_cram_indexes) then select_first([bam_or_cram_indexes])[i] else bam_or_cram_file + index_ext
      String sample = samples[i]

      # Convert to BAM if we have a CRAM
      if (!is_bam) {
        call ctb.CramToBam as CramToBam {
          input:
            cram_file = bam_or_cram_file,
            reference_fasta = reference_fasta,
            reference_index = reference_index,
            requester_pays = requester_pays_crams,
            samtools_cloud_docker = samtools_cloud_docker,
            runtime_attr_override = runtime_attr_cram_to_bam
        }
      }

      File bam_files = select_first([CramToBam.bam_file, bam_or_cram_file])
      File bam_indexes = select_first([CramToBam.bam_index, bam_or_cram_index])
    }

    # Note this will make identical PreprocessIntervals calls across a cohort, but we can rely on
    # call caching to avoid actually repeating calls
    if (collect_coverage) {
      call cov.CollectCoverage as CollectCoverage {
        input:
          intervals = primary_contigs_list,
          blacklist_intervals = coverage_blacklist,
          normal_bams = bam_files,
          normal_bais = bam_indexes,
          samples = samples,
          ref_fasta_dict = reference_dict,
          ref_fasta_fai = reference_index,
          ref_fasta = reference_fasta,
          bin_length = bin_length,
          disabled_read_filters = ["MappingQualityReadFilter"],
          mem_gb_for_preprocess_intervals = mem_gb_for_preprocess_intervals,
          mem_gb_for_collect_counts = mem_gb_for_collect_counts,
          disk_space_gb_for_collect_counts = disk_space_gb_for_collect_counts,
          gatk_docker = gatk_docker
      }
    }

    if (run_delly) {
      scatter (i in range(length(samples))) {
        call delly.Delly as Delly {
          input:
            bam_or_cram_file = bam_files[i],
            bam_or_cram_index = bam_indexes[i],
            sample_id = samples[i],
            reference_fasta = reference_fasta,
            reference_index = reference_index,
            blacklist_intervals_file = select_first([delly_blacklist_intervals_file]),
            sv_types = delly_sv_types,
            sv_base_mini_docker = sv_base_mini_docker,
            delly_docker = select_first([delly_docker]),
            runtime_attr_delly = runtime_attr_delly,
            runtime_attr_gather = runtime_attr_delly_gather
        }
      }
    }

    if (run_manta) {
      scatter (i in range(length(samples))) {
        call manta.Manta as Manta {
          input:
            bam_or_cram_file = bam_files[i],
            bam_or_cram_index = bam_indexes[i],
            sample_id = samples[i],
            reference_fasta = reference_fasta,
            reference_index = reference_index,
            region_bed = manta_region_bed,
            region_bed_index = manta_region_bed_index,
            jobs_per_cpu = manta_jobs_per_cpu,
            mem_gb_per_job = manta_mem_gb_per_job,
            manta_docker = select_first([manta_docker]),
            runtime_attr_override = runtime_attr_manta
        }
      }
    }

    if (collect_pesr) {
      scatter (i in range(length(samples))) {
        call pesr.PESRCollection as PESR {
          input:
            cram = bam_files[i],
            cram_index = bam_indexes[i],
            sample_id = samples[i],
            sv_pipeline_docker = sv_pipeline_docker,
            runtime_attr_override = runtime_attr_pesr
        }
      }
    }

    if (run_wham) {
      scatter (i in range(length(samples))) {
        call wham.Whamg as Wham {
          input:
            bam_or_cram_file = bam_files[i],
            bam_or_cram_index = bam_indexes[i],
            sample_id = samples[i],
            reference_fasta = reference_fasta,
            reference_index = reference_index,
            whitelist_bed_file = wham_whitelist_bed_file,
            chr_file = primary_contigs_list,
            samtools_cloud_docker = samtools_cloud_docker,
            wham_docker = select_first([wham_docker]),
            runtime_attr_whitelist = runtime_attr_wham_whitelist,
            runtime_attr_wham = runtime_attr_wham
        }
      }
    }

    scatter (i in range(length(samples))) {
      # Convert to BAM if we have a CRAM
      if (!is_bam[i]) {
        if (delete_intermediate_bam) {
          File? ctb_coverage_dummy = if (collect_coverage) then select_first([CollectCoverage.counts])[i] else NONE_FILE_
          File? ctb_delly_dummy = if (run_delly) then select_first([Delly.vcf])[i] else NONE_FILE_
          File? ctb_manta_dummy = if (run_manta) then select_first([Manta.vcf])[i] else NONE_FILE_
          File? ctb_pesr_disc_dummy = if (collect_pesr) then select_first([PESR.disc_out])[i] else NONE_FILE_
          File? ctb_pesr_split_dummy = if (collect_pesr) then select_first([PESR.split_out])[i] else NONE_FILE_
          File? ctb_wham_dummy = if (run_wham) then select_first([Wham.vcf])[i] else NONE_FILE_
          Array[File] ctb_dummy = select_all([ctb_coverage_dummy, ctb_delly_dummy, ctb_manta_dummy, ctb_pesr_disc_dummy, ctb_pesr_split_dummy, ctb_wham_dummy])
          call DeleteIntermediateFiles {
            input:
              intermediates = select_all([CramToBam.bam_file[i]]),
              dummy = ctb_dummy
          }
        }
      }
    }
  }

  output {
    Array[File]? BAF_out = if (defined(vcfs)) then BAFFromShardedVCF.baf_files else BAFFromGVCFs.baf_files
    Array[File]? BAF_out_indexes = if (defined(vcfs)) then BAFFromShardedVCF.baf_file_indexes else BAFFromGVCFs.baf_file_indexes

    File? preprocessed_intervals = CollectCoverage.preprocessed_intervals
    Array[File]? coverage_counts = CollectCoverage.counts

    Array[File]? delly_vcf = Delly.vcf
    Array[File]? delly_index = Delly.index

    Array[File]? manta_vcf = Manta.vcf
    Array[File]? manta_index = Manta.index

    Array[File]? pesr_disc = PESR.disc_out
    Array[File]? pesr_disc_index = PESR.disc_out_index
    Array[File]? pesr_split = PESR.split_out
    Array[File]? pesr_split_index = PESR.split_out_index

    Array[File]? wham_vcf = Wham.vcf
    Array[File]? wham_index = Wham.index
  }
}

task DeleteIntermediateFiles {
  input {
    Array[File] intermediates
    Array[File]? dummy # Pass in outputs that must be complete before cleanup
  }
  parameter_meta {
    intermediates: {
      localization_optional: true
    }
    dummy: {
      localization_optional: true
    }
  }

  command {
    gsutil rm -I < ~{write_lines(intermediates)}
  }
  runtime {
    docker: "google/cloud-sdk"
    memory: "1 GB"
    cpu: "1"
    disks: "local-disk 10 HDD"
    preemptible: "3"
    maxRetries: "1"
  }
}
