version 1.0

##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/cnMOPS_hg38/1/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

import "Structs.wdl"

## cnMOPS worflow definition ##
## mode = sex (1=male, 2=female)

workflow CNMOPS {
  input {
    String batch
    File bincov_matrix
    File chrom_file
    File bincov_matrix_index
    File ped_file
    File blacklist
    File allo_file
    Array[String]+ samples
    Float? mem_gb_override_sample10
    Float? mem_gb_override_sample3
    String linux_docker
    String sv_pipeline_docker
    String cnmops_docker
    RuntimeAttr? runtime_attr_sample10
    RuntimeAttr? runtime_attr_sample3
    RuntimeAttr? runtime_attr_ped
    RuntimeAttr? runtime_attr_clean
  }

  Float mem_gb_sample10 = select_first([mem_gb_override_sample10, 40])
  Float mem_gb_sample3 = select_first([mem_gb_override_sample3, 80])
  Array[Array[String]] Allos = read_tsv(allo_file)
  Array[Array[String]] Chroms = read_tsv(chrom_file)

  scatter (Allo in Allos) {
    call CNSampleNormal as Male10 {
      input:
        chr = Allo[0],
        black = blacklist,
        ped = ped_file,
        mode = "1",
        r = "10",
        bincov_matrix = bincov_matrix,
        bincov_matrix_index = bincov_matrix_index,
        mem_gb_override = mem_gb_sample10,
        cnmops_docker = cnmops_docker,
        runtime_attr_override = runtime_attr_sample10
    }

    call CNSampleNormal as Male3 {
      input:
        chr = Allo[0],
        black = blacklist,
        ped = ped_file,
        mode = "1",
        r = "3",
        bincov_matrix = bincov_matrix,
        bincov_matrix_index = bincov_matrix_index,
        mem_gb_override = mem_gb_sample3,
        cnmops_docker = cnmops_docker,
        runtime_attr_override = runtime_attr_sample3
    }
  }

  scatter (Chrom in Chroms) {
    call CNSampleNormal as Normal10 {
      input:
        chr = Chrom[0],
        black = blacklist,
        ped = ped_file,
        mode = "normal",
        r = "10",
        bincov_matrix = bincov_matrix,
        bincov_matrix_index = bincov_matrix_index,
        mem_gb_override = mem_gb_sample10,
        cnmops_docker = cnmops_docker,
        runtime_attr_override = runtime_attr_sample10
    }

    call CNSampleNormal as Normal3 {
      input:
        chr = Chrom[0],
        black = blacklist,
        ped = ped_file,
        mode = "normal",
        r = "3",
        bincov_matrix = bincov_matrix,
        bincov_matrix_index = bincov_matrix_index,
        mem_gb_override = mem_gb_sample3,
        cnmops_docker = cnmops_docker,
        runtime_attr_override = runtime_attr_sample3
    }
  }

  call CNSampleNormal as Female10 {
    input:
      chr = "chrX",
      black = blacklist,
      ped = ped_file,
      mode = "2",
      r = "10",
      bincov_matrix = bincov_matrix,
      bincov_matrix_index = bincov_matrix_index,
      mem_gb_override = mem_gb_sample10,
      cnmops_docker = cnmops_docker,
      runtime_attr_override = runtime_attr_sample10
  }

  call CNSampleNormal as Female3 {
    input:
      chr = "chrX",
      black = blacklist,
      ped = ped_file,
      mode = "2",
      r = "3",
      bincov_matrix = bincov_matrix,
      bincov_matrix_index = bincov_matrix_index,
      mem_gb_override = mem_gb_sample3,
      cnmops_docker = cnmops_docker,
      runtime_attr_override = runtime_attr_sample3
  }

  call GetPed {
    input:
      ped = ped_file,
      samples = samples,
      linux_docker = linux_docker,
      runtime_attr_override = runtime_attr_ped
  }

  call CleanCNMops {
    input:
      samplelist = GetPed.batchped,
      black = blacklist,
      batch = batch,
      N3 = Normal3.Gff,
      N10 = Normal10.Gff,
      M3 = Male3.Gff,
      M10 = Male10.Gff,
      F3 = Female3.Gff,
      F10 = Female10.Gff,
      sv_pipeline_docker = sv_pipeline_docker,
      runtime_attr_override = runtime_attr_ped
  }

  output {
    File Del = CleanCNMops.Del
    File Dup = CleanCNMops.Dup
    File Del_idx = CleanCNMops.Del_idx
    File Dup_idx = CleanCNMops.Dup_idx
  }
}

task GetPed {
  input {
    File ped
    Array[String]+ samples
    String linux_docker
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

  output {
    File batchped = "batch.ped"
  }
  command <<<
    egrep '~{sep="|" samples}' ~{ped} > batch.ped
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: linux_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

task CleanCNMops {
  input {
    File samplelist
    File black
    String batch
    Array[File] N3
    Array[File] N10
    Array[File] M3
    Array[File] M10
    File F3
    File F10
    String sv_pipeline_docker
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

  output {
    File Del = "~{batch}.DEL.header.bed.gz"
    File Del_idx = "~{batch}.DEL.header.bed.gz.tbi"
    File Dup = "~{batch}.DUP.header.bed.gz"
    File Dup_idx = "~{batch}.DUP.header.bed.gz.tbi"
  }
  command <<<

    set -euo pipefail
    cut -f2 ~{samplelist} > sample.list
    cat ~{sep=" "  N3} ~{sep=" "  N10} ~{sep=" "  M3} ~{sep=" "  M10} ~{F3} ~{F10} > cnmops.gff

    mkdir calls
    grep -v "#" cnmops.gff > cnmops.gff1
    echo "./cnmops.gff1">GFF.list
    /opt/WGD/bin/cleancnMOPS.sh -z -o calls/ -S ~{black} sample.list GFF.list

    zcat calls/*/*.cnMOPS.DEL.bed.gz > DELS.bed 
    awk -v batch=~{batch}_DEL_ 'BEGIN{OFS="\t"} {print $1,$2,$3,batch,$4,"cnmops"}' DELS.bed | cat -n |\
    awk 'BEGIN{OFS="\t"} {print $2,$3,$4,$5$1,$6,"DEL",$7}' | sort -k1,1V -k2,2n > ~{batch}.DEL.bed

    cat <(echo -e "#chr\tstart\tend\tname\tsample\tsvtype\tsources") ~{batch}.DEL.bed  > ~{batch}.DEL.header.bed
    bgzip -f ~{batch}.DEL.header.bed
    tabix -f ~{batch}.DEL.header.bed.gz

    zcat calls/*/*.cnMOPS.DUP.bed.gz > DUPS.bed 
    awk -v batch=~{batch}_DUP_ 'BEGIN{OFS="\t"} {print $1,$2,$3,batch,$4,"cnmops"}' DUPS.bed | cat -n |\
    awk 'BEGIN{OFS="\t"} {print $2,$3,$4,$5$1,$6,"DUP",$7}' | sort -k1,1V -k2,2n > ~{batch}.DUP.bed

    cat <(echo -e "#chr\tstart\tend\tname\tsample\tsvtype\tsources") ~{batch}.DUP.bed  > ~{batch}.DUP.header.bed
    bgzip -f ~{batch}.DUP.header.bed
    tabix -f ~{batch}.DUP.header.bed.gz
    
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

task CNSampleNormal {
  input {
    String chr
    File black
    File ped
    String mode
    String r
    File bincov_matrix
    File bincov_matrix_index
    Float? mem_gb_override
    String cnmops_docker
    RuntimeAttr? runtime_attr_override
  }

  parameter_meta {
    bincov_matrix: {
      localization_optional: true
    }
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 30,
    disk_gb: 20,
    boot_disk_gb: 10,
    preemptible_tries: 3, 
    max_retries : 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File Gff = "calls/cnMOPS.cnMOPS.gff"
  }
  command <<<

    set -euo pipefail
    GCS_OAUTH_TOKEN=`gcloud auth application-default print-access-token` \
      tabix -h ~{bincov_matrix} "~{chr}" | sed 's/Start/start/' | sed 's/Chr/chr/' | sed 's/End/end/' > bincov_~{chr}.bed

    if [ ~{mode} == "normal" ]; then  
      mv bincov_~{chr}.bed bincov_~{chr}_~{mode}.bed
    else 
      awk -v sex="~{mode}" '$5==sex' ~{ped} | cut -f2 > ids.to.include
      col=$(head -n 1 bincov_~{chr}.bed | tr '\t' '\n'|cat -n| grep -wf ids.to.include | awk -v ORS="," '{print $1}' | sed 's/,$//g' | sed 's:\([0-9]\+\):$&:g')
      col_a="{print \$1,\$2,\$3,$col}"
      awk -f <(echo "$col_a") bincov_~{chr}.bed | tr ' ' '\t' > bincov_~{chr}_~{mode}.bed
    fi

    bash /opt/WGD/bin/cnMOPS_workflow.sh -S ~{black} -x ~{black} -r ~{r} -o . -M bincov_~{chr}_~{mode}.bed
    
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([mem_gb_override, runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: cnmops_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}
