##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/collectQC_perSample/17/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

# Copyright (c) 2018 Talkowski Laboratory
# Contact: Ryan Collins <rlcollins@g.harvard.edu>
# Distributed under terms of the MIT license.

# Workflow to gather lists of variant IDs per sample from an SV VCF

workflow collectQC_perSample {
  File vcf
  File samples_list
  String prefix
  Int samples_per_shard

  # Shard sample list
  call shard_list {
    input:
      samples_list=samples_list,
      prefix=prefix,
      samples_per_shard=samples_per_shard
  }

  # Collect VCF-wide summary stats per sample list
  scatter (sublist in shard_list.sublists) {
    call collect_VIDs_perSample {
      input:
        vcf=vcf,
        samples_list=sublist,
        prefix=prefix
    }
  }

  # Merge all VID lists into single output directory and tar it
  call merge_shard_VID_lists {
    input:
      tarballs=collect_VIDs_perSample.VID_lists,
      samples_list=samples_list,
      prefix=prefix
  }

  # Final output
  output {
    File VID_lists = merge_shard_VID_lists.VIDs_perSample_tarball
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


# Task to collect list of VIDs per sample
task collect_VIDs_perSample {
  File vcf
  File samples_list
  String prefix

  command <<<
    set -eu
    # For purposes of memory, cut vcf to samples of interest
    idxs=$( zcat ${vcf} | head -n1000 | fgrep "#" | fgrep -v "##" | sed 's/\t/\n/g' \
            | awk -v OFS="\t" '{ print $1, NR }' \
            | fgrep -wf ${samples_list} | cut -f2 | sort -nk1,1 | uniq \
            | paste -s -d, | awk '{ print "1-9,"$1 }' )
    zcat ${vcf} | cut -f"$idxs" \
    | vcftools --vcf - --stdout --non-ref-ac-any 1 --recode --recode-INFO-all \
    | fgrep -v "#" | cut -f3 > VIDs_to_keep.list
    # Gather list of VIDs and genotypes per sample
    cat <( zcat ${vcf} | head -n1000 | fgrep "#" | fgrep -v "##" | cut -f"$idxs" ) \
        <( zcat ${vcf} | fgrep -v "#" | cut -f"$idxs" | fgrep -wf VIDs_to_keep.list ) \
    | /opt/sv-pipeline/scripts/vcf_qc/perSample_vcf_parsing_helper.R \
      /dev/stdin \
      ${samples_list} \
      ${prefix}_perSample_VIDs/

    # Gzip all output lists
    for file in ${prefix}_perSample_VIDs/*.VIDs_genotypes.txt; do
      gzip -f $file
    done

    # Check if one file per sample is present
    if [ $( find ${prefix}_perSample_VIDs/ -name "*.VIDs_genotypes.txt.gz" | wc -l ) -lt $( cat ${samples_list} | sort | uniq | wc -l ) ]; then
      echo "ERROR IN TASK collect_VIDs_perSample! FEWER PER-SAMPLE GENOTYPE FILES LOCATED THAN NUMBER OF INPUT SAMPLES"
      exit 1
    fi

    # Prep output
    tar -czvf ${prefix}.variants_and_genotypes_per_sample.tar.gz \
    ${prefix}_perSample_VIDs
  >>>

  output {
    File VID_lists = "${prefix}.variants_and_genotypes_per_sample.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "7.5 GiB"
    disks: "local-disk 50 HDD"
  }
}


# Task to merge VID lists across shards
task merge_shard_VID_lists {
  Array[File] tarballs
  File samples_list
  String prefix

  command <<<
    set -euo pipefail
    # Create final output directory
    mkdir ${prefix}_perSample_VIDs_merged/
    
    # Iterate over tarballs, unarchive each one, and move all per-sample files
    while read tarball; do
      mkdir results/
      tar -xzvf $tarball --directory results/
      while read file; do
        mv $file ${prefix}_perSample_VIDs_merged/
      done < <( find results/ -name "*.VIDs_genotypes.txt.gz" )
      rm -rf results/
    done < ${write_tsv(tarballs)}

    # Compress final output directory
    tar -czvf ${prefix}_perSample_VIDs.tar.gz \
      ${prefix}_perSample_VIDs_merged
  >>>

  output {
    File VIDs_perSample_tarball = "${prefix}_perSample_VIDs.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
    preemptible: 1
    disks: "local-disk 100 HDD"
  }
}
