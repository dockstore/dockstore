##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/collectQC_external_benchmarking/13/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

# Copyright (c) 2018 Talkowski Laboratory
# Contact: Ryan Collins <rlcollins@g.harvard.edu>
# Distributed under terms of the MIT license.

# Workflow to benchmark an SV VCF vs an external dataset

workflow vcf_external_benchmark {
  File vcfstats
  String ref_build
  String prefix
  String comparator

  # Collect benchmarking results
  call benchmark_vcf {
    input:
      vcfstats=vcfstats,
      ref_build=ref_build,
      prefix=prefix,
      comparator=comparator
  }

  # Return tarball of results
  output {
    File benchmarking_results_tarball = benchmark_vcf.benchmarking_results_tarball
  }
}


# Task to collect external benchmarking data
task benchmark_vcf {
  File vcfstats
  String ref_build
  String prefix
  String comparator

  command <<<
    set -euo pipefail
    # Run benchmarking script
    /opt/sv-pipeline/scripts/vcf_qc/collectQC.external_benchmarking.sh \
    ${vcfstats} \
    /opt/sv-pipeline/ref/vcf_qc_refs/SV_colors.txt \
    ${comparator} \
    collectQC_benchmarking_${comparator}_output/
    
    # Prep outputs
    tar -czvf ${prefix}.collectQC_benchmarking_${comparator}_output.tar.gz \
    collectQC_benchmarking_${comparator}_output
  >>>

  output {
    File benchmarking_results_tarball = "${prefix}.collectQC_benchmarking_${comparator}_output.tar.gz"
  }

  runtime {
    docker: "talkowski/sv-vcf-qc@sha256:b7e902c9d65343cd3e74b817e10aa9692111f7edd5b51cf940383a33d22f7af0"
    preemptible: 1
    memory: "7.5 GiB"
    disks: "local-disk 100 HDD"
  }
}
