version 1.0

import "Structs.wdl"

task FilterVcfBySampleGenotypeAndAddEvidenceAnnotation {
  input {
    File vcf_gz
    String sample_id
    String sv_mini_docker
    String evidence
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(vcf_gz, ".vcf.gz")
  String outfile = "~{filebase}.~{sample_id}.vcf.gz"
  Array[String] sample_array = [sample_id]

  output {
    File out = "~{outfile}"
  }
  command <<<
    set -euo pipefail
    sampleIndex=`gzip -cd ~{vcf_gz} | grep CHROM | cut -f10- | tr "\t" "\n" | awk '$1 == "~{sample_id}" {found=1; print NR - 1} END { if (found != 1) { print "sample not found"; exit 1; }}'`

    bcftools query -f "%CHROM\t%POS\t%REF\t%ALT\t~{evidence}\n" ~{vcf_gz} | bgzip -c > evidence_annotations.tab.gz
    tabix -s1 -b2 -e2 evidence_annotations.tab.gz

    echo '##INFO=<ID=EVIDENCE,Number=.,Type=String,Description="Classes of random forest support.">' > header_line.txt

    bcftools annotate \
        -i "GT[${sampleIndex}]=\"alt\"" \
        -a evidence_annotations.tab.gz \
        -c CHROM,POS,REF,ALT,EVIDENCE \
        -h header_line.txt \
        ~{vcf_gz} | bgzip -c > ~{outfile}
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}


task FilterVcfForShortDepthCalls {
  input {
    File vcf_gz
    Int min_length
    String filter_name

    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(vcf_gz, ".vcf.gz")
  String outfile = "~{filebase}.filter_depth_lt_~{min_length}.vcf.gz"

  output {
    File out = "~{outfile}"
    File out_idx = "~{outfile}.tbi"
  }
  command <<<
    set -euo pipefail

    bcftools filter ~{vcf_gz} \
      -i 'INFO/SVLEN >= ~{min_length} || INFO/ALGORITHMS[*] != "depth"' \
      -s ~{filter_name} |
         bgzip -c > ~{outfile}

    tabix ~{outfile}

  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}


task FilterVcfForCaseSampleGenotype {
  input {
    File vcf_gz
    String sample_id
    String sv_mini_docker

    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(vcf_gz, ".vcf.gz")
  String outfile = "~{filebase}.filter_by_~{sample_id}_gt.vcf.gz"

  output {
    File out = "~{outfile}"
    File out_idx = "~{outfile}.tbi"
  }
  command <<<
    set -euo pipefail
    sampleIndex=`gzip -cd ~{vcf_gz} | grep CHROM | cut -f10- | tr "\t" "\n" | awk '$1 == "~{sample_id}" {found=1; print NR - 1} END { if (found != 1) { print "sample not found"; exit 1; }}'`

    bcftools filter \
        -i "FILTER ~ \"MULTIALLELIC\" || GT[${sampleIndex}]=\"alt\"" \
        ~{vcf_gz} | bgzip -c > ~{outfile}

    tabix ~{outfile}
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}


task FilterVcfWithReferencePanelCalls {
  input {
    File clinical_vcf
    File cohort_vcf

    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(clinical_vcf, ".vcf.gz")
  String outfile = "~{filebase}.filter_by_ref_panel.vcf.gz"

  output {
    File out = "~{outfile}"
    File out_idx = "~{outfile}.tbi"
  }
  command <<<

  set -euo pipefail

  bcftools query -l ~{cohort_vcf} > ref_samples.list

  bcftools query \
      -i 'FILTER !~ "MULTIALLELIC" && (INFO/SVTYPE="DEL" || INFO/SVTYPE="DUP" || INFO/SVTYPE="INV" || INFO/SVTYPE="CPX")' \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{cohort_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}' \
      > cohort.gts.del_dup_inv_cpx.bed

  bcftools query \
      -i 'FILTER !~ "MULTIALLELIC" && (INFO/SVTYPE == "DEL" || INFO/SVTYPE="DUP" || INFO/SVTYPE="INV" || INFO/SVTYPE="CPX") && AC > 0' \
      -S ref_samples.list \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{clinical_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}' \
      > case.ref_panel_variant.del_dup_inv_cpx.bed

  intersectBed \
      -a case.ref_panel_variant.del_dup_inv_cpx.bed \
      -b cohort.gts.del_dup_inv_cpx.bed \
      -f .5 -r -v -wa | cut -f4 > case_variants_not_in_ref_panel.del_dup_inv_cpx.list

  bcftools query \
      -i 'FILTER ~ "MULTIALLELIC"' \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{cohort_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}' \
      > cohort.gts.multiallelic.bed

  bcftools query \
      -i 'FILTER ~ "MULTIALLELIC"' \
      -S ref_samples.list \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{clinical_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}' \
      > case.ref_panel_variant.multiallelic.bed

  intersectBed \
      -a case.ref_panel_variant.multiallelic.bed \
      -b cohort.gts.multiallelic.bed \
      -f .5 -r -v -wa | cut -f4 > multiallelics.list

  bcftools query -i 'SVTYPE="INS"' \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{cohort_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}' > cohort.gts.ins.bed

  bcftools query -i 'SVTYPE == "INS" && AC > 0' \
      -S ref_samples.list \
      -f '%CHROM\t%POS\t%END\t%ID\t%SVLEN\n' \
      ~{clinical_vcf} | \
      awk '{OFS="\t"; $3 = $2 + $5; print}'  > case.ref_panel_variant.ins.bed

  intersectBed \
      -a case.ref_panel_variant.ins.bed \
      -b cohort.gts.ins.bed \
      -f .5 -r -v -wa | cut -f4 > case_variants_not_in_ref_panel.ins.list

  bcftools query -i 'SVTYPE="BND"' \
      -f '%CHROM\t%POS\t%INFO/CHR2\t%INFO/END\t%ID\t\n' \
      ~{cohort_vcf} | \
      awk '{OFS="\t"; print $1,$2-50,$2+50,$3,$4-50,$4+50,$5}' > cohort.gts.bnd.bedpe

  bcftools query -i 'SVTYPE="BND" && AC > 0' \
      -S ref_samples.list \
      -f '%CHROM\t%POS\t%INFO/CHR2\t%INFO/END\t%ID\t\n' \
      ~{clinical_vcf} | \
      awk '{OFS="\t"; print $1,$2-50,$2+50,$3,$4-50,$4+50,$5}' > case.gts.bnd.bedpe

  pairToPair -a case.gts.bnd.bedpe \
      -b cohort.gts.bnd.bedpe \
      -type both |\
      cut -f7 | sort -u > case_bnds_to_keep.list

  cat case_variants_not_in_ref_panel.del_dup_inv_cpx.list \
               case_variants_not_in_ref_panel.ins.list  > case_variants_not_in_ref_panel.list

  bcftools filter \
      -e 'ID=@case_variants_not_in_ref_panel.list || ( SVTYPE="BND" && ID!=@case_bnds_to_keep.list ) || (FILTER ~ "MULTIALLELIC" && ID!=@multiallelics.list )' \
      -s REF_PANEL_GENOTYPES \
      -m + \
      ~{clinical_vcf} | bgzip -c > ~{outfile}
  tabix ~{outfile}
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

task ResetHighSRBackgroundFilter {
  input {
    File clinical_vcf
    File clinical_vcf_idx

    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(clinical_vcf, ".vcf.gz")
  String outfile = "~{filebase}.reset_filters.vcf.gz"

  output {
    File out = "~{outfile}"
    File out_idx = "~{outfile}.tbi"
  }
  command <<<

  set -euo pipefail

  echo '##INFO=<ID=HIGH_SR_BACKGROUND,Number=0,Type=Flag,Description="Sites with high split read background">' > header.txt

  bcftools filter -i 'FILTER ~ "HIGH_SR_BACKGROUND"' ~{clinical_vcf} | bgzip -c > hsrb.vcf.gz
  tabix hsrb.vcf.gz

  bcftools annotate \
    -k \
    -a hsrb.vcf.gz \
    -m HIGH_SR_BACKGROUND \
    -h header.txt \
    -x FILTER/HIGH_SR_BACKGROUND \
    ~{clinical_vcf} | bgzip -c > ~{outfile}

  tabix ~{outfile}
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

task FinalVCFCleanup {
  input {
    File clinical_vcf
    File clinical_vcf_idx

    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  String filebase = basename(clinical_vcf, ".vcf.gz")
  String outfile = "~{filebase}.final_cleanup.vcf.gz"

  output {
    File out = "~{outfile}"
    File out_idx = "~{outfile}.tbi"
  }
  command <<<

     set -euo pipefail

     # clean up bad END tags
     bcftools query -f '%CHROM\t%POS\t%REF\t%ALT\t%INFO/END\n' \
        ~{clinical_vcf} | awk '$5 < $2 {OFS="\t"; $5 = $2 + 1; print}' | bgzip -c > bad_ends.txt.gz
     tabix -s1 -b2 -e2 bad_ends.txt.gz
     bcftools annotate \
        -a bad_ends.txt.gz \
        -c CHROM,POS,REF,ALT,END \
        ~{clinical_vcf} | bgzip -c > ~{outfile}
     tabix ~{outfile}
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

task RewriteSRCoords {
  input {
    File vcf
    File metrics
    File cutoffs
    String prefix
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 10,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File annotated_vcf = "${prefix}.corrected_coords.vcf.gz"
  }
  command <<<

    set -euo pipefail

    /opt/sv-pipeline/03_variant_filtering/scripts/rewrite_SR_coords.py ~{vcf} ~{metrics} ~{cutoffs} stdout \
      | vcf-sort -c \
      | bgzip -c \
      > ~{prefix}.corrected_coords.vcf.gz

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
