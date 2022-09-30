##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/clowther/binCov_batch_CRAM_copy/3/wdl
## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/bincov_merge_chr/1/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

## Copyright Broad Institute, 2017
## 
## Contact: Ryan L. Collins <rlcollins@g.harvard.edu>
## 
## This WDL pipeline implements batched sequencing coverage metadata collection QC
##
## LICENSING : 
## This script is released under the WDL source code license (BSD-3) (see LICENSE in 
## https://github.com/broadinstitute/wdl). Note however that the programs it calls may 
## be subject to different licenses. Users are responsible for checking that they are
## authorized to run all programs before running this script. Please see the docker 
## page at https://hub.docker.com/r/broadinstitute/genomes-in-the-cloud/ for detailed
## licensing information pertaining to the included programs.

version 1.0

import "Structs.wdl"

# Workflow to run binCov QC on a single sample
workflow BincovQC {
  input {
    File counts_file
    String sample
    File genome_file
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_qc
  }

  call BincovQC {
    input:
      counts_file = counts_file,
      genome_file = genome_file,
      sample_name = sample,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr_override = runtime_attr_qc
  }

  output {
    File qc_result = BincovQC.result
    File qc_raw_chr = BincovQC.raw_chr
    File qc_raw_chr_index = BincovQC.raw_chr_index
  }
}

task BincovQC {
  input {
    File counts_file
    File genome_file
    String sample_name
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  String qc_filename = "${sample_name}_bincov.QC.txt"
  String raw_chr_filename = "${sample_name}_raw.bincov.bed"
  String raw_chr_gz_filename = "${sample_name}_raw.bincov.bed.gz"
  String raw_chr_index_filename = raw_chr_gz_filename + ".tbi"

  RuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 16, 
    disk_gb: 500,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File result = qc_filename
    File raw_chr = raw_chr_gz_filename
    File raw_chr_index = raw_chr_index_filename
  }
  command <<<

    set -euo pipefail

    # Convert to bincov format, remove header, no need to sort
    zcat ~{counts_file} \
      | sed '/^@/d' \
      | sed 1d \
      | awk 'BEGIN{OFS="\t"}{print $1,$2-1,$3,$4}' \
      | grep -v "^#\|^@" > ~{raw_chr_filename}

    # Run QC
    awk -v distance=1000000 -v OFS="\t" '{ print $1, $2-distance, $2 }' ~{genome_file} > regions.bed
    cat ~{raw_chr_filename} | bedtools intersect -a - -b regions.bed > ~{sample_name}_input.bincov.bed 
    Rscript /opt/WGD/bin/bincov_QC.R ~{sample_name}_input.bincov.bed ~{sample_name} ~{qc_filename}

    bgzip -f ~{raw_chr_filename}
    tabix -f ~{raw_chr_gz_filename}
  
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_pipeline_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

