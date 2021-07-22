/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.helpers;

import static org.junit.Assert.*;

import org.junit.Test;

public class CheckUrlHelperTest {

    @Test
    public void getUrls() {
        CheckUrlHelper.getUrls("{\n"
            + "  \"TopMedVariantCaller.ref_1000G_omni2_5_b38_sites_PASS_vcf_gz\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1000G_omni2.5.b38.sites.PASS.vcf.gz\",\n"
            + "  \"TopMedVariantCaller.ref_1000G_omni2_5_b38_sites_PASS_vcf_gz_tbi\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1000G_omni2.5.b38.sites.PASS.vcf.gz.tbi\",\n"
            + "  \"TopMedVariantCaller.chr10_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr10.vcf\",\n"
            + "  \"TopMedVariantCaller.chr11_KI270927v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr11_KI270927v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr11_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr11.vcf\",\n"
            + "  \"TopMedVariantCaller.chr12_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr12.vcf\",\n"
            + "  \"TopMedVariantCaller.chr13_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr13.vcf\",\n"
            + "  \"TopMedVariantCaller.chr14_GL000009v2_random_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr14_GL000009v2_random.vcf\",\n"
            + "  \"TopMedVariantCaller.chr14_KI270846v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr14_KI270846v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr14_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr14.vcf\",\n"
            + "  \"TopMedVariantCaller.chr15_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr15.vcf\",\n"
            + "  \"TopMedVariantCaller.chr16_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr16.vcf\",\n"
            + "  \"TopMedVariantCaller.chr17_KI270857v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr17_KI270857v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr17_KI270862v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr17_KI270862v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr17_KI270909v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr17_KI270909v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr17_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr17.vcf\",\n"
            + "  \"TopMedVariantCaller.chr18_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr18.vcf\",\n"
            + "  \"TopMedVariantCaller.chr19_KI270938v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr19_KI270938v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr19_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr19.vcf\",\n"
            + "  \"TopMedVariantCaller.chr1_KI270706v1_random_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr1_KI270706v1_random.vcf\",\n"
            + "  \"TopMedVariantCaller.chr1_KI270766v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr1_KI270766v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr1_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr1.vcf\",\n"
            + "  \"TopMedVariantCaller.chr20_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr20.vcf\",\n"
            + "  \"TopMedVariantCaller.chr21_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr21.vcf\",\n"
            + "  \"TopMedVariantCaller.chr22_KI270879v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr22_KI270879v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr22_KI270928v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr22_KI270928v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr22_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr22.vcf\",\n"
            + "  \"TopMedVariantCaller.chr2_KI270773v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr2_KI270773v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr2_KI270894v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr2_KI270894v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr2_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr2.vcf\",\n"
            + "  \"TopMedVariantCaller.chr3_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr3.vcf\",\n"
            + "  \"TopMedVariantCaller.chr4_GL000008v2_random_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr4_GL000008v2_random.vcf\",\n"
            + "  \"TopMedVariantCaller.chr4_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr4.vcf\",\n"
            + "  \"TopMedVariantCaller.chr5_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr5.vcf\",\n"
            + "  \"TopMedVariantCaller.chr6_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr6.vcf\",\n"
            + "  \"TopMedVariantCaller.chr7_KI270803v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr7_KI270803v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr7_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr7.vcf\",\n"
            + "  \"TopMedVariantCaller.chr8_KI270821v1_alt_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr8_KI270821v1_alt.vcf\",\n"
            + "  \"TopMedVariantCaller.chr8_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr8.vcf\",\n"
            + "  \"TopMedVariantCaller.chr9_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chr9.vcf\",\n"
            + "  \"TopMedVariantCaller.chrUn_KI270742v1_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chrUn_KI270742v1.vcf\",\n"
            + "  \"TopMedVariantCaller.chrX_vcf\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/1kg.pilot_release.merged.indels.sites.hg38.chrX.vcf\",\n"
            + "  \"TopMedVariantCaller.ref_dbsnp_142_b38_vcf_gz\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/dbsnp_142.b38.vcf.gz\",\n"
            + "  \"TopMedVariantCaller.ref_dbsnp_142_b38_vcf_gz_tbi\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/dbsnp_142.b38.vcf.gz.tbi\",\n"
            + "  \"TopMedVariantCaller.ref_dbsnp_All_vcf_gz\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/dbsnp.All.vcf.gz\",\n"
            + "  \"TopMedVariantCaller.ref_dbsnp_All_vcf_gz_tbi\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/dbsnp.All.vcf.gz.tbi\",\n"
            + "  \"TopMedVariantCaller.ref_hapmap_3_3_b38_sites_vcf_gz\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hapmap_3.3.b38.sites.vcf.gz\",\n"
            + "  \"TopMedVariantCaller.ref_hapmap_3_3_b38_sites_vcf_gz_tbi\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hapmap_3.3.b38.sites.vcf.gz.tbi\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_bs_umfa\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH-bs.umfa\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_dict\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.dict\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_alt\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.alt\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_amb\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.amb\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_ann\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.ann\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_bwt\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.bwt\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_fai\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.fai\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_pac\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.pac\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_fa_sa\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.fa.sa\",\n"
            + "  \"TopMedVariantCaller.ref_hs38DH_winsize100_gc\":  \"https://storage.googleapis.com/topmed_workflow_testing/topmed_variant_caller/reference_files/hg38/hs38DH.winsize100.gc\",\n"
            + "\n"
            + "  \"TopMedVariantCaller.docker_image\": \"quay.io/ucsc_cgl/topmed_freeze3_calling:1.9.0\"\n"
            + "}\n"
            + "\n");
    }
}