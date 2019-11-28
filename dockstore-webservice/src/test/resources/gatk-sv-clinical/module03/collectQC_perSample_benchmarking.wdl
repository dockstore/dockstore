##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/collectQC_perSample_benchmarking/19/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

# Copyright (c) 2018 Talkowski Laboratory
# Contact: Ryan Collins <rlcollins@g.harvard.edu>
# Distributed under terms of the MIT license.

# Workflow to perform per-sample benchmarking from an SV VCF vs an external dataset

workflow persample_external_benchmark {
  File vcfstats
  File samples_list
  File persample_tarball
  File comparison_tarball
  String prefix
  String comparison_set_name
  String ref_build
  Int samples_per_shard

  # Shard sample list
  call shard_list {
    input:
      samples_list=samples_list,
      prefix=prefix,
      samples_per_shard=samples_per_shard
  }

  # Collect benchmarking results per sample list shard
  scatter (sublist in shard_list.sublists) {
    call benchmark_samples {
      input:
        vcfstats=vcfstats,
        samples_list=sublist,
        persample_tarball=persample_tarball,
        comparison_tarball=comparison_tarball,
        prefix=prefix,
        comparison_set_name=comparison_set_name,
        ref_build=ref_build
    }
  }

  # Merge all results per shard into single output directory and tar it
  call merge_shard_results {
    input:
      tarballs=benchmark_samples.benchmarking_results_tarball,
      samples_list=samples_list,
      prefix=prefix,
      comparison_set_name=comparison_set_name
  }

  # Return tarball of results
  output {
    File benchmarking_results_tarball = merge_shard_results.perSample_results_tarball
  }
}


# Task to shard a list of sample IDs into evenly-split sublists
task shard_list {
  File samples_list
  String prefix
  Int samples_per_shard

  command <<<
    #Split list
    /opt/sv-pipeline/04_variant_resolution/scripts/evenSplitter.R \
      --shuffle \
      -L ${samples_per_shard} \
      ${samples_list} \
      ${prefix}.list_shard.
  >>>

  output {
    Array[File] sublists = glob("${prefix}.list_shard.*")
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
  }
}


# Task to collect per-sample benchmarking stats
task benchmark_samples {
  File vcfstats
  File samples_list
  File persample_tarball
  File comparison_tarball
  String prefix
  String comparison_set_name
  String ref_build

  command <<<
    set -euo pipefail
    # Run benchmarking script
    mkdir ${prefix}_${comparison_set_name}_perSample_results/
    /opt/sv-pipeline/scripts/vcf_qc/collectQC.perSample_benchmarking.sh \
    -r ${ref_build} \
    -p ${comparison_set_name} \
    ${vcfstats} \
    ${samples_list} \
    ${persample_tarball} \
    ${comparison_tarball} \
    ${prefix}_${comparison_set_name}_perSample_results/
    
    # Prep outputs
    tar -czvf ${prefix}_${comparison_set_name}_perSample_results.tar.gz \
    ${prefix}_${comparison_set_name}_perSample_results
  >>>

  output {
    File benchmarking_results_tarball = "${prefix}_${comparison_set_name}_perSample_results.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "7.5 GiB"
    disks: "local-disk 100 HDD"
  }
}


# Task to merge benchmarking results across shards
task merge_shard_results {
  Array[File] tarballs
  File samples_list
  String prefix
  String comparison_set_name

  command <<<
    set -euo pipefail
    # Create final output directory
    mkdir ${prefix}_${comparison_set_name}_results_merged/
    
    # Iterate over tarballs, unarchive each one, and move all per-sample files
    while read tarball; do
      mkdir results/
      tar -xzvf $tarball --directory results/
      while read file; do
        mv $file ${prefix}_${comparison_set_name}_results_merged/
      done < <( find results/ -name "*.sensitivity.bed.gz" )
      while read file; do
        mv $file ${prefix}_${comparison_set_name}_results_merged/
      done < <( find results/ -name "*.specificity.bed.gz" )
      rm -rf results/
    done < ${write_tsv(tarballs)}

    # Compress final output directory
    tar -czvf ${prefix}_${comparison_set_name}_results_merged.tar.gz \
      ${prefix}_${comparison_set_name}_results_merged
  >>>

  output {
    File perSample_results_tarball = "${prefix}_${comparison_set_name}_results_merged.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
    preemptible: 1
    disks: "local-disk 100 HDD"
  }
}
