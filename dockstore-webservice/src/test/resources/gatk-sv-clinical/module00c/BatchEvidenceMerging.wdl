##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/00_batch_evidence_merging/15/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

version 1.0

import "Structs.wdl"

workflow EvidenceMerging {
  input {
    Array[File]? BAF_files
    Array[File]+ PE_files
    Array[File]+ SR_files
    File inclusion_bed
    String batch

    #Single file size estimates
    Int? BAF_size_mb
    Int? PE_size_mb
    Int? SR_size_mb

    Int? disk_overhead_gb   # Fixed extra disk
    String sv_mini_docker
    RuntimeAttr? runtime_attr_pesr   # Struct disk space is ignored
  }

  Int overhead_gb = select_first([disk_overhead_gb, 10])

  if (defined(BAF_files)) {
     Array[File] BAF_files_value = select_first([BAF_files])
     scatter (i in range(length(BAF_files_value))) {
        File BAF_indexes = BAF_files_value[i] + ".tbi"
     }

    #Estimate required disk space based on number of input files (as of CW v34, size(Array[File]) is prohibitively slow)
    Int BAF_disk_gb = overhead_gb + ceil((length(BAF_files_value) * select_first([BAF_size_mb, 500])) / 1000)
    call MergeEvidenceFiles as MergeBAFFiles {
        input:
        files = BAF_files_value,
        indexes = BAF_indexes,
        batch = batch,
        evidence = "BAF",
        inclusion_bed = inclusion_bed,
        disk_gb_override = BAF_disk_gb,
        sv_mini_docker = sv_mini_docker,
        runtime_attr_override = runtime_attr_pesr
    }
  }

  #Estimate required disk space based on number of input files (as of CW v34, size(Array[File]) is prohibitively slow)
  Int PE_disk_gb = overhead_gb + ceil((length(PE_files) * select_first([PE_size_mb, 2000])) / 1000)
  Int SR_disk_gb = overhead_gb + ceil((length(SR_files) * select_first([SR_size_mb, 6000])) / 1000)

  scatter (i in range(length(PE_files))) {
    File PE_indexes = PE_files[i] + ".tbi"
    File SR_indexes = SR_files[i] + ".tbi"
  }

  call MergeEvidenceFiles as MergeSRFiles {
    input:
      files = SR_files,
      indexes = SR_indexes,
      batch = batch,
      evidence = "SR",
      inclusion_bed = inclusion_bed,
      disk_gb_override = SR_disk_gb,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_pesr
  }
  call MergeEvidenceFiles as MergePEFiles {
    input:
      files = PE_files,
      indexes = PE_indexes,
      batch = batch,
      evidence = "PE",
      inclusion_bed = inclusion_bed,
      disk_gb_override = PE_disk_gb,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_pesr
  }

  output {
    File? merged_BAF = MergeBAFFiles.merged
    File? merged_BAF_idx = MergeBAFFiles.merged_idx
    File merged_SR = MergeSRFiles.merged
    File merged_SR_idx = MergeSRFiles.merged_idx
    File merged_PE = MergePEFiles.merged
    File merged_PE_idx = MergePEFiles.merged_idx
  }
}

task MergeEvidenceFiles {
  input {
    Array[File] files
    Array[File] indexes
    String batch
    String evidence
    File inclusion_bed
    Int? disk_gb_override  # Overrides runtime_attr
    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 3.75, 
    disk_gb: 100, 
    boot_disk_gb: 20,
    preemptible_tries: 0,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File merged = "~{batch}.~{evidence}.txt.gz"
    File merged_idx = "~{batch}.~{evidence}.txt.gz.tbi"
  }
  command <<<

    set -euxo pipefail

    # TODO: fail fast if localization failed. This is a Cromwell issue.
    while read file; do
      if [ ! -f $file ]; then
        echo "Localization failed: ${file}" >&2
        exit 1
      fi
      if [ ! -f "${file}.tbi" ]; then
        echo "Index not found in expected path: ${file}.tbi" >&2
        exit 1
      fi
    done < ~{write_lines(files)};

    mkdir data
    while read file; do
      filename=`basename $file`
      tabix -h -R ~{inclusion_bed} $file > data/$filename.txt
    done < ~{write_lines(files)};

    tmpdir=$(mktemp -d);
    sort -m -k1,1V -k2,2n -T $tmpdir data/*.txt | bgzip -c > ~{batch}.~{evidence}.txt.gz;
    tabix -f -s1 -b 2 -e 2 ~{batch}.~{evidence}.txt.gz
  
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([disk_gb_override, runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}
