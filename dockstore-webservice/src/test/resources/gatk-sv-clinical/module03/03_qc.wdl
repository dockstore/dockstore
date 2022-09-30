##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/03_variant_filtering/27/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

import "master_SV_VCF_QC.wdl" as vcf_qc

workflow variant_filtering_qc {

  File manta_vcf_noOutliers
  File delly_vcf_noOutliers
  File melt_vcf_noOutliers
  File depth_vcf_noOutliers
  File merged_pesr_vcf
  String batch
  File famfile
  String ref_build
  File Sanders_2015_tarball
  File Collins_2017_tarball
  File Werling_2018_tarball

call vcf_qc.master_vcf_qc as delly_qc {
    input:
      vcf=delly_vcf_noOutliers,
      famfile=famfile,
      ref_build=ref_build,
      prefix="${batch}.delly_03_filtered_vcf",
      sv_per_shard=10000,
      samples_per_shard=100,
      Sanders_2015_tarball=Sanders_2015_tarball,
      Collins_2017_tarball=Collins_2017_tarball,
      Werling_2018_tarball=Werling_2018_tarball
  }

  call vcf_qc.master_vcf_qc as manta_qc {
    input:
      vcf=manta_vcf_noOutliers,
      famfile=famfile,
      ref_build=ref_build,
      prefix="${batch}.manta_03_filtered_vcf",
      sv_per_shard=10000,
      samples_per_shard=100,
      Sanders_2015_tarball=Sanders_2015_tarball,
      Collins_2017_tarball=Collins_2017_tarball,
      Werling_2018_tarball=Werling_2018_tarball
  }

  call vcf_qc.master_vcf_qc as melt_qc {
    input:
      vcf=melt_vcf_noOutliers,
      famfile=famfile,
      ref_build=ref_build,
      prefix="${batch}.melt_03_filtered_vcf",
      sv_per_shard=10000,
      samples_per_shard=100,
      Sanders_2015_tarball=Sanders_2015_tarball,
      Collins_2017_tarball=Collins_2017_tarball,
      Werling_2018_tarball=Werling_2018_tarball
  }

  call vcf_qc.master_vcf_qc as pesr_qc {
    input:
      vcf=merged_pesr_vcf,
      famfile=famfile,
      ref_build=ref_build,
      prefix="${batch}.pesr_merged_03_filtered_vcf",
      sv_per_shard=10000,
      samples_per_shard=100,
      Sanders_2015_tarball=Sanders_2015_tarball,
      Collins_2017_tarball=Collins_2017_tarball,
      Werling_2018_tarball=Werling_2018_tarball
  }

  call vcf_qc.master_vcf_qc as depth_qc {
    input:
      vcf=depth_vcf_noOutliers,
      famfile=famfile,
      ref_build=ref_build,
      prefix="${batch}.depth_03_filtered_vcf",
      sv_per_shard=10000,
      samples_per_shard=100,
      Sanders_2015_tarball=Sanders_2015_tarball,
      Collins_2017_tarball=Collins_2017_tarball,
      Werling_2018_tarball=Werling_2018_tarball
  }

  output {
    File filtered_delly_vcf_qc = delly_qc.sv_vcf_qc_output
    File filtered_manta_vcf_qc = manta_qc.sv_vcf_qc_output
    File filtered_melt_vcf_qc = melt_qc.sv_vcf_qc_output
    File filtered_pesr_vcf_qc = pesr_qc.sv_vcf_qc_output
    File filtered_depth_vcf_qc = depth_qc.sv_vcf_qc_output
  }

}
