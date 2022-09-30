##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/master_SV_VCF_QC/60/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

import "collectQC_vcfwide.wdl" as collectQC_vcfwide
import "collectQC_external_benchmarking.wdl" as collectQC_external_benchmarking
import "collectQC_perSample.wdl" as collectQC_perSample
import "collectQC_perSample_benchmarking.wdl" as collectQC_perSample_benchmarking

# Copyright (c) 2018 Talkowski Laboratory
# Contact: Ryan Collins <rlcollins@g.harvard.edu>
# Distributed under terms of the MIT license.

# Master workflow to perform comprehensive quality control (QC) on 
# an SV VCF output by the Talkowski lab SV pipeline

workflow master_vcf_qc {
  File vcf
  File famfile
  String ref_build
  String prefix
  Int sv_per_shard
  Int samples_per_shard
  File Sanders_2015_tarball
  File Collins_2017_tarball
  File Werling_2018_tarball

  # Shard input VCF for stats collection
  call shard_vcf {
    input:
      vcf=vcf,
      sv_per_shard=sv_per_shard
  }

  # Gather stats for each split
  scatter (shard in shard_vcf.shard_vcfs) {
    # Collect VCF-wide summary stats
    call collectQC_vcfwide.collectQC_vcfwide as collectQC_vcfwide {
      input:
        vcf=shard,
        famfile=famfile,
        ref_build=ref_build,
        prefix="${prefix}.shard."
    }

    # Run vcf2bed for record purposes
    call vcf2bed {
      input:
        vcf=shard,
        prefix="${prefix}.shard"
    }
  }

  # Merge shards into single VCF stats file
  call merge_shards as merge_vcfwide_stat_shards {
    input:
      vcf_stats=collectQC_vcfwide.vcfstats,
      prefix=prefix
  }

  # Merge vcf2bed output
  call merge_vcf2bed {
    input:
      vcf2bed_shards=vcf2bed.vcf2bed_out,
      prefix=prefix
  }

  # Plot VCF-wide summary stats
  call plotQC_vcfwide {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      prefix=prefix
  }

  # Collect external benchmarking vs 1000G
  call collectQC_external_benchmarking.vcf_external_benchmark as ThousandG_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      ref_build=ref_build,
      prefix=prefix,
      comparator="1000G_Sudmant"
  }

  # Plot external benchmarking vs 1000G
  call plotQC_external_benchmarking as ThousandG_plot {
    input:
      benchmarking_tarball=ThousandG_benchmark.benchmarking_results_tarball,
      prefix=prefix,
      comparator="1000G_Sudmant"
  }
  
  # Collect external benchmarking vs ASC
  call collectQC_external_benchmarking.vcf_external_benchmark as ASC_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      ref_build=ref_build,
      prefix=prefix,
      comparator="ASC_Werling"
  }

  # Plot external benchmarking vs ASC
  call plotQC_external_benchmarking as ASC_plot {
    input:
      benchmarking_tarball=ASC_benchmark.benchmarking_results_tarball,
      prefix=prefix,
      comparator="ASC_Werling"
  }
  
  # Collect external benchmarking vs HGSV
  call collectQC_external_benchmarking.vcf_external_benchmark as HGSV_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      ref_build=ref_build,
      prefix=prefix,
      comparator="HGSV_Chaisson"
  }

  # Plot external benchmarking vs HGSV
  call plotQC_external_benchmarking as HGSV_plot {
    input:
      benchmarking_tarball=HGSV_benchmark.benchmarking_results_tarball,
      prefix=prefix,
      comparator="HGSV_Chaisson"
  }

  # Collect per-sample VID lists
  call collectQC_perSample.collectQC_perSample as collectQC_perSample {
    input:
      vcf=vcf,
      samples_list=collectQC_vcfwide.samples_list[0],
      prefix=prefix,
      samples_per_shard=samples_per_shard
  }

  # Plot per-sample stats
  call plotQC_perSample {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      perSample_tarball=collectQC_perSample.VID_lists,
      prefix=prefix
  }

  # Plot per-family stats
  call plotQC_perFamily {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      famfile=famfile,
      perSample_tarball=collectQC_perSample.VID_lists,
      prefix=prefix
  }

  # Collect per-sample external benchmarking vs Sanders 2015 arrays
  call collectQC_perSample_benchmarking.persample_external_benchmark as Sanders_perSample_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      persample_tarball=collectQC_perSample.VID_lists,
      comparison_tarball=Sanders_2015_tarball,
      prefix=prefix,
      comparison_set_name="Sanders_2015_array",
      ref_build=ref_build,
      samples_per_shard=samples_per_shard
  }

  # Plot per-sample external benchmarking vs Sanders 2015 arrays
  call plotQC_perSample_benchmarking as Sanders_perSample_plot {
    input:
      perSample_benchmarking_tarball=Sanders_perSample_benchmark.benchmarking_results_tarball,
      samples_list=collectQC_vcfwide.samples_list[0],
      comparison_set_name="Sanders_2015_array",
      prefix=prefix
  }

  # Collect per-sample external benchmarking vs Collins 2017 liWGS
  call collectQC_perSample_benchmarking.persample_external_benchmark as Collins_perSample_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      persample_tarball=collectQC_perSample.VID_lists,
      comparison_tarball=Collins_2017_tarball,
      prefix=prefix,
      comparison_set_name="Collins_2017_liWGS",
      ref_build=ref_build,
      samples_per_shard=samples_per_shard
  }

  # Plot per-sample external benchmarking vs Collins 2017 liWGS
  call plotQC_perSample_benchmarking as Collins_perSample_plot {
    input:
      perSample_benchmarking_tarball=Collins_perSample_benchmark.benchmarking_results_tarball,
      samples_list=collectQC_vcfwide.samples_list[0],
      comparison_set_name="Collins_2017_liWGS",
      prefix=prefix
  }

  # Collect per-sample external benchmarking vs Werling 2018 WGS
  call collectQC_perSample_benchmarking.persample_external_benchmark as Werling_perSample_benchmark {
    input:
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      samples_list=collectQC_vcfwide.samples_list[0],
      persample_tarball=collectQC_perSample.VID_lists,
      comparison_tarball=Werling_2018_tarball,
      prefix=prefix,
      comparison_set_name="Werling_2018_WGS",
      ref_build=ref_build,
      samples_per_shard=samples_per_shard
  }

  # Plot per-sample external benchmarking vs Werling 2018 WGS
  call plotQC_perSample_benchmarking as Werling_perSample_plot {
    input:
      perSample_benchmarking_tarball=Werling_perSample_benchmark.benchmarking_results_tarball,
      samples_list=collectQC_vcfwide.samples_list[0],
      comparison_set_name="Werling_2018_WGS",
      prefix=prefix
  }

  # Sanitize all outputs
  call sanitize_outputs {
    input:
      prefix=prefix,
      samples_list=collectQC_vcfwide.samples_list[0],
      vcfstats=merge_vcfwide_stat_shards.merged_vcf_stats,
      vcfstats_idx=merge_vcfwide_stat_shards.merged_vcf_stats_idx,
      plotQC_vcfwide_tarball=plotQC_vcfwide.plots_tarball,
      plotQC_external_benchmarking_ThousandG_tarball=ThousandG_plot.tarball_wPlots,
      plotQC_external_benchmarking_ASC_tarball=ASC_plot.tarball_wPlots,
      plotQC_external_benchmarking_HGSV_tarball=HGSV_plot.tarball_wPlots,
      collectQC_perSample_tarball=collectQC_perSample.VID_lists,
      plotQC_perSample_tarball=plotQC_perSample.perSample_plots_tarball,
      plotQC_perFamily_tarball=plotQC_perFamily.perFamily_plots_tarball,
      cleaned_famfile=plotQC_perFamily.cleaned_famfile,
      plotQC_perSample_Sanders_tarball=Sanders_perSample_plot.perSample_plots_tarball,
      plotQC_perSample_Collins_tarball=Collins_perSample_plot.perSample_plots_tarball,
      plotQC_perSample_Werling_tarball=Werling_perSample_plot.perSample_plots_tarball
  }

  # Final output
  output {
    # File vcf_stats = merge_vcfwide_stat_shards.merged_vcf_stats
    # File vcfwide_plots = plotQC_vcfwide.plots_tarball
    # File ThousandG_plots = ThousandG_plot.tarball_wPlots
    # File VID_lists = collectQC_perSample.VID_lists
    # File perSample_plots = plotQC_perSample.perSample_plots_tarball
    # File perFamily_plots = plotQC_perFamily.perFamily_plots_tarball
    # File Sanders_2015_results = Sanders_perSample_benchmark.benchmarking_results_tarball
    # File Werling_2018_results = Werling_perSample_benchmark.benchmarking_results_tarball
    File sv_vcf_qc_output = sanitize_outputs.vcf_qc_tarball
    File vcf2bed_output = merge_vcf2bed.merged_vcf2bed_out
  }
}


# Shard VCF into fixed size chunks
task shard_vcf {
  File vcf
  Int sv_per_shard

  command {
    /opt/sv-pipeline/scripts/shard_VCF.sh \
      ${vcf} \
      ${sv_per_shard} \
      "vcf.shard."
  }

  output {
    Array[File] shard_vcfs = glob("vcf.shard.*.vcf.gz")
  }
  
  runtime {
    preemptible: 1
    docker: "talkowski/sv-vcf-qc@sha256:0a84a24a0924d667f3d2d7142c0db5fe316918e87053e0e60635fd69954fb720"
    memory: "7.5 GiB"
    disks: "local-disk 2000 HDD"
  }
}


# Run vcf2bed on an input vcf
task vcf2bed {
  File vcf
  String prefix

  command {
    set -euo pipefail
    svtk vcf2bed --info ALL \
      ${vcf} \
      stdout \
      | bgzip -c \
      > "${prefix}.vcf2bed.bed.gz"
  }

  output {
    File vcf2bed_out = "${prefix}.vcf2bed.bed.gz"
  }
  
  runtime {
    preemptible: 1
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
    memory: "3.75 GiB"
    disks: "local-disk 25 HDD"
  }
}


# Merge shards after VCF stats collection
task merge_shards {
  Array[File] vcf_stats
  String prefix

  command <<<
    set -euo pipefail
    while read split; do
      zcat $split | fgrep -v "#";
    done < ${write_tsv(vcf_stats)} | sort -Vk1,1 -k2,2n -k3,3n | \
    cat <(zcat ${vcf_stats[0]} | head -n1) - > ${prefix}.VCF_sites.stats.bed;
    bgzip -f ${prefix}.VCF_sites.stats.bed;
    tabix -f ${prefix}.VCF_sites.stats.bed.gz
  >>>

  output {
    File merged_vcf_stats = "${prefix}.VCF_sites.stats.bed.gz"
    File merged_vcf_stats_idx = "${prefix}.VCF_sites.stats.bed.gz.tbi"
  }
  
  runtime {
     preemptible: 1
    docker: "talkowski/sv-vcf-qc@sha256:0a84a24a0924d667f3d2d7142c0db5fe316918e87053e0e60635fd69954fb720"
    memory: "7.5 GiB"
    disks: "local-disk 200 HDD"
  }
}


# Merge vcf2bed shards
task merge_vcf2bed {
  Array[File] vcf2bed_shards
  String prefix

  command <<<
    set -euo pipefail
    zcat ${vcf2bed_shards[0]} | sed -n '1p' > header.txt
    zcat ${sep=' ' vcf2bed_shards} | fgrep -v "#" \
      | sort -Vk1,1 -k2,2n -k3,3n \
      | cat header.txt - \
      | bgzip -c \
      > "${prefix}.vcf2bed.bed.gz"
  >>>

  output {
    File merged_vcf2bed_out = "${prefix}.vcf2bed.bed.gz"
  }
  
  runtime {
     preemptible: 1
    docker: "talkowski/sv-vcf-qc@sha256:0a84a24a0924d667f3d2d7142c0db5fe316918e87053e0e60635fd69954fb720"
    memory: "3.75 GiB"
    disks: "local-disk 200 HDD"
  }
}


# Plot VCF-wide QC stats
task plotQC_vcfwide {
  File vcfstats
  File samples_list
  String prefix

  command <<<
    set -euo pipefail
    # Plot VCF-wide distributions
    /opt/sv-pipeline/scripts/vcf_qc/plot_sv_vcf_distribs.R \
      -N $( cat ${samples_list} | sort | uniq | wc -l ) \
      -S /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
      ${vcfstats} \
      plotQC_vcfwide_output/

    # Prep outputs
    tar -czvf ${prefix}.plotQC_vcfwide_output.tar.gz \
      plotQC_vcfwide_output
  >>>

  output {
    File plots_tarball = "${prefix}.plotQC_vcfwide_output.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "15 GiB"
    disks: "local-disk 30 HDD"
  }
}


# Plot external benchmarking results
task plotQC_external_benchmarking {
  File benchmarking_tarball
  String prefix
  String comparator

  command <<<    
    set -euo pipefail
    # Plot benchmarking stats
    /opt/sv-pipeline/scripts/vcf_qc/plotQC.external_benchmarking.helper.sh \
      ${benchmarking_tarball} \
      ${comparator}

    # Prep outputs
    tar -czvf ${prefix}.collectQC_benchmarking_${comparator}_output.wPlots.tar.gz \
    collectQC_benchmarking_${comparator}_output
  >>>

  output {
    File tarball_wPlots = "${prefix}.collectQC_benchmarking_${comparator}_output.wPlots.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "15 GiB"
    disks: "local-disk 30 HDD"
  }
}


# Plot per-sample stats
task plotQC_perSample {
  File vcfstats
  File samples_list
  File perSample_tarball
  String prefix

  command <<<
    set -euo pipefail
    # Make per-sample directory
    mkdir ${prefix}_perSample/

    # Untar per-sample VID lists
    mkdir tmp_untar/
    tar -xvzf ${perSample_tarball} \
      --directory tmp_untar/
    while read file; do
      mv $file ${prefix}_perSample/
    done < <( find tmp_untar/ -name "*.VIDs_genotypes.txt.gz" )

    # Plot per-sample distributions
    /opt/sv-pipeline/scripts/vcf_qc/plot_sv_perSample_distribs.R \
      -S /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
      ${vcfstats} \
      ${samples_list} \
      ${prefix}_perSample/ \
      ${prefix}_perSample_plots/

    # Prepare output
    tar -czvf ${prefix}.plotQC_perSample.tar.gz \
      ${prefix}_perSample_plots
  >>>

  output {
    File perSample_plots_tarball = "${prefix}.plotQC_perSample.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "15 GiB"
    disks: "local-disk 50 HDD"
  }
}


# Plot per-family stats
task plotQC_perFamily {
  File vcfstats
  File samples_list
  File famfile
  File perSample_tarball
  String prefix

  command <<<
    set -euo pipefail
    # Clean fam file
    /opt/sv-pipeline/scripts/vcf_qc/cleanFamFile.sh \
      ${samples_list} \
      ${famfile} \
      cleaned.fam

    # Only run if any families remain after cleaning
    if [ $( fgrep -v "#" cleaned.fam | wc -l ) -gt 0 ]; then

      # Make per-sample directory
      mkdir ${prefix}_perSample/

      # Untar per-sample VID lists
      mkdir tmp_untar/
      tar -xvzf ${perSample_tarball} \
        --directory tmp_untar/
      while read file; do
        mv $file ${prefix}_perSample/
      done < <( find tmp_untar/ -name "*.VIDs_genotypes.txt.gz" )

      # Run family analysis
      /opt/sv-pipeline/scripts/vcf_qc/analyze_fams.R \
        -S /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
        ${vcfstats} \
        cleaned.fam \
        ${prefix}_perSample/ \
        ${prefix}_perFamily_plots/

    else

      mkdir ${prefix}_perFamily_plots/

    fi

    # Prepare output
    tar -czvf ${prefix}.plotQC_perFamily.tar.gz \
      ${prefix}_perFamily_plots

  >>>

  output {
    File perFamily_plots_tarball = "${prefix}.plotQC_perFamily.tar.gz"
    File cleaned_famfile = "cleaned.fam"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "30 GiB"
    disks: "local-disk 100 HDD"
  }
}


# Plot per-sample benchmarking
task plotQC_perSample_benchmarking {
  File perSample_benchmarking_tarball
  File samples_list
  String comparison_set_name
  String prefix

  command <<<
    set -euo pipefail
    # Untar per-sample benchmarking results
    mkdir tmp_untar/
    tar -xvzf ${perSample_benchmarking_tarball} \
      --directory tmp_untar/
    mkdir results/
    while read file; do
      mv $file results/
    done < <( find tmp_untar/ -name "*.sensitivity.bed.gz" )
    while read file; do
      mv $file results/
    done < <( find tmp_untar/ -name "*.specificity.bed.gz" )

    # Plot per-sample benchmarking
    /opt/sv-pipeline/scripts/vcf_qc/plot_perSample_benchmarking.R \
      -c ${comparison_set_name} \
      results/ \
      ${samples_list} \
      /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
      ${prefix}.${comparison_set_name}_perSample_benchmarking_plots/

    # Prepare output
    tar -czvf ${prefix}.${comparison_set_name}_perSample_benchmarking_plots.tar.gz \
      ${prefix}.${comparison_set_name}_perSample_benchmarking_plots
  >>>

  output {
    File perSample_plots_tarball = "${prefix}.${comparison_set_name}_perSample_benchmarking_plots.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    memory: "15 GiB"
    disks: "local-disk 50 HDD"
  }
}


# Sanitize final output
task sanitize_outputs {
  String prefix
  File samples_list
  File vcfstats
  File vcfstats_idx
  File plotQC_vcfwide_tarball
  File plotQC_external_benchmarking_ThousandG_tarball
  File plotQC_external_benchmarking_ASC_tarball
  File plotQC_external_benchmarking_HGSV_tarball
  File collectQC_perSample_tarball
  File plotQC_perSample_tarball
  File plotQC_perFamily_tarball
  File cleaned_famfile
  File plotQC_perSample_Sanders_tarball
  File plotQC_perSample_Collins_tarball
  File plotQC_perSample_Werling_tarball

  command <<<
    set -euo pipefail
    # Prep output directory tree
    mkdir ${prefix}_SV_VCF_QC_output/
    mkdir ${prefix}_SV_VCF_QC_output/data/
    mkdir ${prefix}_SV_VCF_QC_output/data/variant_info_per_sample/
    mkdir ${prefix}_SV_VCF_QC_output/plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/main_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/vcf_summary_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/1000G_Sudmant_benchmarking_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/ASC_Werling_benchmarking_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/HGSV_Chaisson_benchmarking_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/per_sample_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/sv_inheritance_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Sanders_2015_array_perSample_benchmarking_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Collins_2017_liWGS_perSample_benchmarking_plots/
    mkdir ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Werling_2018_WGS_perSample_benchmarking_plots/

    # Process VCF-wide stats
    cp ${vcfstats} \
      ${prefix}_SV_VCF_QC_output/data/${prefix}.VCF_sites.stats.bed.gz
    cp ${vcfstats_idx} \
      ${prefix}_SV_VCF_QC_output/data/${prefix}.VCF_sites.stats.bed.gz.tbi

    # Process VCF-wide plots
    tar -xzvf ${plotQC_vcfwide_tarball}
    cp plotQC_vcfwide_output/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp plotQC_vcfwide_output/supporting_plots/vcf_summary_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/vcf_summary_plots/

    # Process 1000G benchmarking stats & plots
    tar -xzvf ${plotQC_external_benchmarking_ThousandG_tarball}
    cp collectQC_benchmarking_1000G_Sudmant_output/data/1000G_Sudmant.SV.ALL.overlaps.bed.gz* \
      ${prefix}_SV_VCF_QC_output/data/
    cp collectQC_benchmarking_1000G_Sudmant_output/plots/1000G_Sudmant_ALL_samples/main_plots/VCF_QC.1000G_Sudmant_ALL.callset_benchmarking.png \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp -r collectQC_benchmarking_1000G_Sudmant_output/plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/1000G_Sudmant_benchmarking_plots/

    # Process ASC benchmarking stats & plots
    tar -xzvf ${plotQC_external_benchmarking_ASC_tarball}
    cp collectQC_benchmarking_ASC_Werling_output/data/ASC_Werling.SV.ALL.overlaps.bed.gz* \
      ${prefix}_SV_VCF_QC_output/data/
    cp collectQC_benchmarking_ASC_Werling_output/plots/ASC_Werling_ALL_samples/main_plots/VCF_QC.ASC_Werling_ALL.callset_benchmarking.png \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp -r collectQC_benchmarking_ASC_Werling_output/plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/ASC_Werling_benchmarking_plots/

    # Process HGSV benchmarking stats & plots
    tar -xzvf ${plotQC_external_benchmarking_HGSV_tarball}
    cp collectQC_benchmarking_HGSV_Chaisson_output/data/HGSV_Chaisson.SV.ALL.overlaps.bed.gz* \
      ${prefix}_SV_VCF_QC_output/data/
    cp collectQC_benchmarking_HGSV_Chaisson_output/plots/HGSV_Chaisson_ALL_samples/main_plots/VCF_QC.HGSV_Chaisson_ALL.callset_benchmarking.png \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp -r collectQC_benchmarking_HGSV_Chaisson_output/plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/HGSV_Chaisson_benchmarking_plots/

    # Process per-sample stats
    tar -xzvf ${collectQC_perSample_tarball}
    cp ${prefix}_perSample_VIDs_merged/*.VIDs_genotypes.txt.gz \
      ${prefix}_SV_VCF_QC_output/data/variant_info_per_sample/

    # Process per-sample plots
    tar -xzvf ${plotQC_perSample_tarball}
    cp ${prefix}_perSample_plots/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp ${prefix}_perSample_plots/supporting_plots/per_sample_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/per_sample_plots/

    # Process per-family plots
    tar -xzvf ${plotQC_perFamily_tarball}
    cp ${prefix}_perFamily_plots/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp ${prefix}_perFamily_plots/supporting_plots/sv_inheritance_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/sv_inheritance_plots/

    # Process Sanders per-sample benchmarking plots
    tar -xzvf ${plotQC_perSample_Sanders_tarball}
    cp ${prefix}.Sanders_2015_array_perSample_benchmarking_plots/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp ${prefix}.Sanders_2015_array_perSample_benchmarking_plots/supporting_plots/per_sample_benchmarking_Sanders_2015_array/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Sanders_2015_array_perSample_benchmarking_plots/

    # Process Collins per-sample benchmarking plots
    tar -xzvf ${plotQC_perSample_Collins_tarball}
    cp ${prefix}.Collins_2017_liWGS_perSample_benchmarking_plots/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp ${prefix}.Collins_2017_liWGS_perSample_benchmarking_plots/supporting_plots/per_sample_benchmarking_Collins_2017_liWGS/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Collins_2017_liWGS_perSample_benchmarking_plots/

    # Process Werling per-sample benchmarking plots
    tar -xzvf ${plotQC_perSample_Werling_tarball}
    cp ${prefix}.Werling_2018_WGS_perSample_benchmarking_plots/main_plots/* \
      ${prefix}_SV_VCF_QC_output/plots/main_plots/
    cp ${prefix}.Werling_2018_WGS_perSample_benchmarking_plots/supporting_plots/per_sample_benchmarking_Werling_2018_WGS/* \
      ${prefix}_SV_VCF_QC_output/plots/supplementary_plots/Werling_2018_WGS_perSample_benchmarking_plots/

    # Process misc files
    cp ${cleaned_famfile} \
      ${prefix}_SV_VCF_QC_output/data/${prefix}.cleaned_trios.fam
    cp ${samples_list} \
      ${prefix}_SV_VCF_QC_output/data/${prefix}.samples_analyzed.list

    # Compress final output
    tar -czvf ${prefix}_SV_VCF_QC_output.tar.gz \
      ${prefix}_SV_VCF_QC_output
  >>>

  output {
    File vcf_qc_tarball = "${prefix}_SV_VCF_QC_output.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
     preemptible: 1
    disks: "local-disk 100 HDD"
  }
}
