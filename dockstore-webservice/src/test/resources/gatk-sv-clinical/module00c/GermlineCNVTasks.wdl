version 1.0

task AnnotateIntervals {
    input {
      File intervals
      File ref_fasta
      File ref_fasta_fai
      File ref_fasta_dict
      File? mappability_track_bed
      File? mappability_track_bed_idx
      File? segmental_duplication_track_bed
      File? segmental_duplication_track_bed_idx
      Int? feature_query_lookahead
      File? gatk4_jar_override

      # Runtime parameters
      String gatk_docker
      Int? mem_gb
      Int? disk_space_gb
      Boolean use_ssd = false
      Int? cpu
      Int? preemptible_attempts
    }

    Int machine_mem_mb = select_first([mem_gb, 2]) * 1000
    Int command_mem_mb = machine_mem_mb - 500

    # Determine output filename
    String base_filename = basename(intervals, ".interval_list")

    command <<<
        set -euo pipefail
        export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk4_jar_override}

        gatk --java-options "-Xmx~{command_mem_mb}m" AnnotateIntervals \
            -L ~{intervals} \
            --reference ~{ref_fasta} \
            ~{"--mappability-track " + mappability_track_bed} \
            ~{"--segmental-duplication-track " + segmental_duplication_track_bed} \
            --feature-query-lookahead ~{default=1000000 feature_query_lookahead} \
            --interval-merging-rule OVERLAPPING_ONLY \
            --output ~{base_filename}.annotated.tsv
    >>>

    runtime {
        docker: "~{gatk_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, ceil(size(ref_fasta, "GB")) + 50]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        File annotated_intervals = "~{base_filename}.annotated.tsv"
    }
}

task FilterIntervals {
    input {
      File intervals
      File? blacklist_intervals
      File? annotated_intervals
      Array[File] read_count_files
      Float? minimum_gc_content
      Float? maximum_gc_content
      Float? minimum_mappability
      Float? maximum_mappability
      Float? minimum_segmental_duplication_content
      Float? maximum_segmental_duplication_content
      Int? low_count_filter_count_threshold
      Float? low_count_filter_percentage_of_samples
      Float? extreme_count_filter_minimum_percentile
      Float? extreme_count_filter_maximum_percentile
      Float? extreme_count_filter_percentage_of_samples
      File? gatk4_jar_override

      # Runtime parameters
      String gatk_docker
      Int? mem_gb
      Int? disk_space_gb
      Boolean use_ssd = false
      Int? cpu
      Int? preemptible_attempts
    }

    Int machine_mem_mb = select_first([mem_gb, 7]) * 1000
    Int command_mem_mb = machine_mem_mb - 500

    # Determine output filename
    String base_filename = basename(intervals, ".interval_list")

    command <<<
        set -euo pipefail
        export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk4_jar_override}

        read_count_files_list=~{write_lines(read_count_files)}
        grep gz$ $read_count_files_list | xargs -l1 -P0 gunzip
        sed 's/\.gz$//' $read_count_files_list | \
            awk '{print "--input "$0}' > read_count_files.args

        gatk --java-options "-Xmx~{command_mem_mb}m" FilterIntervals \
            -L ~{intervals} \
            ~{"-XL " + blacklist_intervals} \
            ~{"--annotated-intervals " + annotated_intervals} \
            --arguments_file read_count_files.args \
            --minimum-gc-content ~{default="0.1" minimum_gc_content} \
            --maximum-gc-content ~{default="0.9" maximum_gc_content} \
            --minimum-mappability ~{default="0.9" minimum_mappability} \
            --maximum-mappability ~{default="1.0" maximum_mappability} \
            --minimum-segmental-duplication-content ~{default="0.0" minimum_segmental_duplication_content} \
            --maximum-segmental-duplication-content ~{default="0.5" maximum_segmental_duplication_content} \
            --low-count-filter-count-threshold ~{default="5" low_count_filter_count_threshold} \
            --low-count-filter-percentage-of-samples ~{default="90.0" low_count_filter_percentage_of_samples} \
            --extreme-count-filter-minimum-percentile ~{default="1.0" extreme_count_filter_minimum_percentile} \
            --extreme-count-filter-maximum-percentile ~{default="99.0" extreme_count_filter_maximum_percentile} \
            --extreme-count-filter-percentage-of-samples ~{default="90.0" extreme_count_filter_percentage_of_samples} \
            --interval-merging-rule OVERLAPPING_ONLY \
            --output ~{base_filename}.filtered.interval_list
    >>>

    runtime {
        docker: "~{gatk_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, 50]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        File filtered_intervals = "~{base_filename}.filtered.interval_list"
    }
}

task ScatterIntervals {
    input{
      File interval_list
      Int num_intervals_per_scatter
      String? output_dir
      File? gatk4_jar_override

      # Runtime parameters
      String gatk_docker
      Int? mem_gb
      Int? disk_space_gb
      Boolean use_ssd = false
      Int? cpu
      Int? preemptible_attempts
    }

    Int machine_mem_mb = select_first([mem_gb, 2]) * 1000
    Int command_mem_mb = machine_mem_mb - 500

    # If optional output_dir not specified, use "out";
    String output_dir_ = select_first([output_dir, "out"])

    String base_filename = basename(interval_list, ".interval_list")

    command <<<
        set -euo pipefail
        mkdir ~{output_dir_}
        export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk4_jar_override}

        {
            >&2 echo "Attempting to run IntervalListTools..."
            gatk --java-options "-Xmx~{command_mem_mb}m" IntervalListTools \
                --INPUT ~{interval_list} \
                --SUBDIVISION_MODE INTERVAL_COUNT \
                --SCATTER_CONTENT ~{num_intervals_per_scatter} \
                --OUTPUT ~{output_dir_} &&
            # output files are named output_dir_/temp_0001_of_N/scattered.interval_list, etc. (N = num_intervals_per_scatter);
            # we rename them as output_dir_/base_filename.scattered.0000.interval_list, etc.
            ls ~{output_dir_}/*/scattered.interval_list | \
                cat -n | \
                while read n filename; do mv $filename ~{output_dir_}/~{base_filename}.scattered.$(printf "%04d" $n).interval_list; done
        } || {
            # if only a single shard is required, then we can just rename the original interval list
            >&2 echo "IntervalListTools failed because only a single shard is required. Copying original interval list..."
            cp ~{interval_list} ~{output_dir_}/~{base_filename}.scattered.1.interval_list
        }
    >>>

    runtime {
        docker: "~{gatk_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, 40]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        Array[File] scattered_interval_lists = glob("~{output_dir_}/~{base_filename}.scattered.*.interval_list")
    }
}

task PostprocessGermlineCNVCalls {
    input {
      String entity_id
      Array[File] gcnv_calls_tars
      Array[File] gcnv_model_tars
      Array[File] calling_configs
      Array[File] denoising_configs
      Array[File] gcnvkernel_version
      Array[File] sharded_interval_lists
      File contig_ploidy_calls_tar
      Array[String]? allosomal_contigs
      Int ref_copy_number_autosomal_contigs
      Int sample_index
      File? gatk4_jar_override

      # Runtime parameters
      String gatk_docker
      Int? mem_gb
      Int? disk_space_gb
      Boolean use_ssd = false
      Int? cpu
      Int? preemptible_attempts
    }

    Int machine_mem_mb = select_first([mem_gb, 7]) * 1000
    Int command_mem_mb = machine_mem_mb - 1000

    String genotyped_intervals_vcf_filename = "genotyped-intervals-~{entity_id}.vcf.gz"
    String genotyped_segments_vcf_filename = "genotyped-segments-~{entity_id}.vcf.gz"

    Array[String] allosomal_contigs_args = if defined(allosomal_contigs) then prefix("--allosomal-contig ", select_first([allosomal_contigs])) else []

    command <<<
        set -euo pipefail
        export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk4_jar_override}

        sharded_interval_lists_array=(~{sep=" " sharded_interval_lists})

        # untar calls to CALLS_0, CALLS_1, etc directories and build the command line
        # also copy over shard config and interval files
        gcnv_calls_tar_array=(~{sep=" " gcnv_calls_tars})
        calling_configs_array=(~{sep=" " calling_configs})
        denoising_configs_array=(~{sep=" " denoising_configs})
        gcnvkernel_version_array=(~{sep=" " gcnvkernel_version})
        sharded_interval_lists_array=(~{sep=" " sharded_interval_lists})
        calls_args=""
        for index in ${!gcnv_calls_tar_array[@]}; do
            gcnv_calls_tar=${gcnv_calls_tar_array[$index]}
            mkdir -p CALLS_$index/SAMPLE_~{sample_index}
            tar xzf $gcnv_calls_tar -C CALLS_$index/SAMPLE_~{sample_index}
            cp ${calling_configs_array[$index]} CALLS_$index/
            cp ${denoising_configs_array[$index]} CALLS_$index/
            cp ${gcnvkernel_version_array[$index]} CALLS_$index/
            cp ${sharded_interval_lists_array[$index]} CALLS_$index/
            calls_args="$calls_args --calls-shard-path CALLS_$index"
        done

        # untar models to MODEL_0, MODEL_1, etc directories and build the command line
        gcnv_model_tar_array=(~{sep=" " gcnv_model_tars})
        model_args=""
        for index in ${!gcnv_model_tar_array[@]}; do
            gcnv_model_tar=${gcnv_model_tar_array[$index]}
            mkdir MODEL_$index
            tar xzf $gcnv_model_tar -C MODEL_$index
            model_args="$model_args --model-shard-path MODEL_$index"
        done

        mkdir extracted-contig-ploidy-calls
        tar xzf ~{contig_ploidy_calls_tar} -C extracted-contig-ploidy-calls

        gatk --java-options "-Xmx~{command_mem_mb}m" PostprocessGermlineCNVCalls \
            $calls_args \
            $model_args \
            ~{sep=" " allosomal_contigs_args} \
            --autosomal-ref-copy-number ~{ref_copy_number_autosomal_contigs} \
            --contig-ploidy-calls extracted-contig-ploidy-calls \
            --sample-index ~{sample_index} \
            --output-genotyped-intervals ~{genotyped_intervals_vcf_filename} \
            --output-genotyped-segments ~{genotyped_segments_vcf_filename}

        rm -r CALLS_*
        rm -r MODEL_*
    >>>

    runtime {
        docker: "~{gatk_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, 40]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        File genotyped_intervals_vcf = genotyped_intervals_vcf_filename
        File genotyped_segments_vcf = genotyped_segments_vcf_filename
    }
}



task ExplodePloidyCalls {
    input {
      File contig_ploidy_calls_tar
      Array[String] samples

      # Runtime parameters
      String linux_docker
      Int? mem_gb
      Int? disk_space_gb
      Boolean use_ssd = false
      Int? cpu
      Int? preemptible_attempts
    }

    Int num_samples = length(samples)
    String out_dir = "calls_renamed"

    Int machine_mem_mb = select_first([mem_gb, 3]) * 1000
    Int command_mem_mb = machine_mem_mb - 500

    command <<<
      set -euo pipefail

      # Extract ploidy calls
      mkdir calls
      tar xzf ~{contig_ploidy_calls_tar} -C calls/

      # Archive call files by sample, renaming so they will be glob'd in order
      sample_ids=(~{sep=" " samples})
      for (( i=0; i<~{num_samples}; i++ ))
      do
        sample_id=${sample_ids[$i]}
        sample_no=`printf %03d $i`
        tar -czf sample_${sample_no}.${sample_id}.contig_ploidy_calls.tar.gz -C calls/SAMPLE_${i} .
      done
    >>>

    runtime {
        docker: "~{linux_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, 40]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        Array[File] sample_contig_ploidy_calls_tar = glob("sample_*.contig_ploidy_calls.tar.gz")
    }
}

task BundlePostprocessingInvariants {
    input {
        Array[File] calls_tars
        Array[File] model_tars
    }

    command <<<
        set -euo pipefail
        mkdir -p out

        calls_files_tar_list=~{write_lines(calls_tars)}
        model_files_tar_list=~{write_lines(model_tars)}

        cat $calls_files_tar_list | sort -V > calls_files_tar_list.sorted
        cat $model_files_tar_list | sort -V > model_files_tar_list.sorted

        paste calls_files_tar_list.sorted model_files_tar_list.sorted |\
              awk '{print (NR-1)"\t"$0}' > file_pairs.sorted
        OIFS=$IFS
        IFS=$'\t'
        while read index calls_tar model_tar; do
            mkdir -p out/CALLS_$index
            mkdir -p out/MODEL_$index
            tar xzf $calls_tar -C out/CALLS_$index
            tar xzf $model_tar -C out/MODEL_$index
            rm $calls_tar $model_tar
        done < file_pairs.sorted
        IFS=$OIFS

        tar c -C out . | gzip -1 > case-gcnv-postprocessing-invariants.tar.gz
        rm -Rf out
    >>>

    runtime {
       docker: "talkowski/sv-pipeline"
       disks: "local-disk 150 HDD"
       memory: "2 GB"
       preemptible: 0 #select_first([preemptible_attempts, 5])
       maxRetries: 1
    }

    output {
        File bundle_tar = "case-gcnv-postprocessing-invariants.tar.gz"
    }
}

task BundledPostprocessGermlineCNVCalls {
    input {
        File invariants_tar
        String entity_id
        File contig_ploidy_calls_tar
        Array[String]? allosomal_contigs
        Int ref_copy_number_autosomal_contigs
        Int sample_index
        File? gatk4_jar_override

        # Runtime parameters
        String gatk_docker
        Int? mem_gb
        Int? disk_space_gb
        Boolean use_ssd = false
        Int? cpu
        Int? preemptible_attempts
    }

    Int machine_mem_mb = select_first([mem_gb, 7]) * 1000
    Int command_mem_mb = machine_mem_mb - 1000

    Float invariants_size = size(invariants_tar, "GiB")
    Float disk_overhead = 20.0
    Float tar_disk_factor= 5.0
    Int vm_disk_size = ceil(tar_disk_factor * invariants_size + disk_overhead)

    String genotyped_intervals_vcf_filename = "genotyped-intervals-~{entity_id}.vcf.gz"
    String genotyped_segments_vcf_filename = "genotyped-segments-~{entity_id}.vcf.gz"
    Boolean allosomal_contigs_specified = defined(allosomal_contigs)

    command <<<
        set -euo pipefail
        
        export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk4_jar_override}

        # untar calls to CALLS_0, CALLS_1, etc directories and build the command line
        # also copy over shard config and interval files
        time tar xzf ~{invariants_tar}
        rm ~{invariants_tar}
        number_of_shards=`find . -name 'CALLS_*' | wc -l` 

        touch calls_and_model_args.txt
        for i in $(seq 0 `expr $number_of_shards - 1`); do 
            echo "--calls-shard-path CALLS_$i" >> calls_and_model_args.txt
            echo "--model-shard-path MODEL_$i" >> calls_and_model_args.txt
        done

        mkdir -p extracted-contig-ploidy-calls
        tar xzf ~{contig_ploidy_calls_tar} -C extracted-contig-ploidy-calls
        rm ~{contig_ploidy_calls_tar}

        allosomal_contigs_args="--allosomal-contig ~{sep=" --allosomal-contig " allosomal_contigs}"

        time gatk --java-options "-Xmx~{command_mem_mb}m" PostprocessGermlineCNVCalls \
             --arguments_file calls_and_model_args.txt \
            ~{true="$allosomal_contigs_args" false="" allosomal_contigs_specified} \
            --autosomal-ref-copy-number ~{ref_copy_number_autosomal_contigs} \
            --contig-ploidy-calls extracted-contig-ploidy-calls \
            --sample-index ~{sample_index} \
            --output-genotyped-intervals ~{genotyped_intervals_vcf_filename} \
            --output-genotyped-segments ~{genotyped_segments_vcf_filename}

        rm -Rf extracted-contig-ploidy-calls
    >>>

    runtime {
        docker: "~{gatk_docker}"
        memory: machine_mem_mb + " MB"
        disks: "local-disk " + select_first([disk_space_gb, vm_disk_size]) + if use_ssd then " SSD" else " HDD"
        cpu: select_first([cpu, 1])
        preemptible: select_first([preemptible_attempts, 5])
        maxRetries: 1
    }

    output {
        File genotyped_intervals_vcf = genotyped_intervals_vcf_filename
        File genotyped_segments_vcf = genotyped_segments_vcf_filename
    }
}
