##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/collectQC_vcfwide/26/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

# Copyright (c) 2018 Talkowski Laboratory
# Contact: Ryan Collins <rlcollins@g.harvard.edu>
# Distributed under terms of the MIT license.

# Workflow to gather SV VCF summary stats for an input VCF

workflow collectQC_vcfwide {
  File vcf
  File famfile
  String ref_build
  String prefix

  # Collect VCF-wide summary stats
  call collect_vcf_stats {
    input:
      vcf=vcf,
      famfile=famfile,
      ref_build=ref_build,
      prefix=prefix
  }

  # Final output
  output {
    File vcfstats=collect_vcf_stats.vcf_stats
    File vcfstats_idx=collect_vcf_stats.vcf_stats
    File samples_list=collect_vcf_stats.samples_list
    File vcfwide_tarball=collect_vcf_stats.vcfwide_tarball
  }
}


# Task to collect VCF-wide QC stats
task collect_vcf_stats {
  File vcf
  File famfile
  String ref_build
  String prefix

  command <<<
    set -euo pipefail
    # Run QC script
    /opt/sv-pipeline/scripts/vcf_qc/collectQC.vcf_wide.sh \
      ${vcf} \
      /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
      collectQC_vcfwide_output/
    
    # Prep outputs
    cp collectQC_vcfwide_output/data/VCF_sites.stats.bed.gz \
      ${prefix}.VCF_sites.stats.bed.gz
    cp collectQC_vcfwide_output/data/VCF_sites.stats.bed.gz.tbi \
      ${prefix}.VCF_sites.stats.bed.gz.tbi
    cp collectQC_vcfwide_output/analysis_samples.list \
      ${prefix}.analysis_samples.list
    tar -czvf ${prefix}.collectQC_vcfwide_output.tar.gz \
      collectQC_vcfwide_output
  >>>

  output {
    File vcf_stats = "${prefix}.VCF_sites.stats.bed.gz"
    File vcf_stats_idx = "${prefix}.VCF_sites.stats.bed.gz.tbi"
    File samples_list = "${prefix}.analysis_samples.list"
    File vcfwide_tarball = "${prefix}.collectQC_vcfwide_output.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "7.5 GiB"
    disks: "local-disk 30 HDD"
  }
}
