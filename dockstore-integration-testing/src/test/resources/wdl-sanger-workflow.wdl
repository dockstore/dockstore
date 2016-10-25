import "https://raw.githubusercontent.com/DockstoreTestUser/dockstore-whalesay/master/Dockstore.wdl" as httpimport
import "wdl.wdl" as localimport
task get_basename {
  File f

  command {
    basename ${f} | cut -f 1 -d '.'
  }

  output {
    String base = read_string(stdout())
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task getSampleId {
  File inBam

  command {
     samtools view -H ${inBam} | grep -m 1 "SM:" | sed 's/.*SM:\(.*\).*/\1/g'
  }

  output {
    String SM = read_string(stdout())
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task compareGenotype {
  File controlBam
  File controlBamBai
  String controlBamId
  File tumorBam
  File tumorBamBai
  String tumorBamId
  String outputDir = "genotype"

  command {
    mkdir -p ${outputDir};
    compareBamGenotypes.pl \
    -o ${outputDir} \
    -nb ${controlBam} \
    -j ${outputDir + "/summary.json"} \
    -tb ${tumorBam}
  }

  output {
    File genotypeSummary = "${outputDir}/summary.json"
    File controlGender = "${outputDir}/${controlBamId}.full_gender.tsv"
    File controlGenotype = "${outputDir}/${controlBamId}.full_genotype.tsv"
    File tumorGender = "${outputDir}/${tumorBamId}.full_gender.tsv"
    File tumorGenotype = "${outputDir}/${tumorBamId}.full_genotype.tsv"
    File tumorVsControlGenotype = "${outputDir}/${tumorBamId}_vs_${controlBamId}.genotype.txt"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task analyzeContamination {
  File bamFile
  File bamIndexFile
  String SM
  File? ascatSegmentFile
  Int contamDownsampOneIn = 25
  String process
  String outputDir = "contamination"

  command <<<
    mkdir -p ${outputDir};
    if [ ${process} == "tumor" ]; then
      verifyBamHomChk.pl \
      -o ${outputDir} \
      -b ${bamFile} \
      -d ${contamDownsampOneIn} \
      -j ${outputDir + "/" + SM + "_summary.json"} \
      -a ${ascatSegmentFile}
    else
      verifyBamHomChk.pl \
      -o ${outputDir} \
      -b ${bamFile} \
      -d ${contamDownsampOneIn} \
      -j ${outputDir + "/" + SM + "_summary.json"}
    fi
  >>>

  output {
    File summary = "${outputDir}/${SM}_summary.json"
    File depthRG = "${outputDir}/${SM}.depthRG"
    File selfRG = "${outputDir}/${SM}.selfRG"
    File depthSM = "${outputDir}/${SM}.depthSM"
    File selfSM = "${outputDir}/${SM}.selfSM"
    File snps = "${outputDir}/${SM}_snps.vcf"
    File log = "${outputDir}/${SM}.log"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task bam_stats {
  File bamFile
  File bamIndexFile
  String bamFileName

  command {
    bam_stats -i ${bamFile} \
              -o ${bamFileName + ".bam.bas"}
  }

  output {
    File basFile = "${bamFileName}.bam.bas"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task bbAlleleCount {
  File bamFile
  File bamIndexFile
  File bbRefLoci
  String bbRefName
  String SM
  String outputDir = "bbCounts"

  command {
    mkdir -p ${outputDir};
    alleleCounter \
    -l ${bbRefLoci} \
    -o ${outputDir + "/" + SM + "_" + bbRefName + ".tsv"} \
    -b ${bamFile}
  }

  output {
    File alleleCounts = "${outputDir}/${SM}_${bbRefName}.tsv"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

# How do we pass a directory of files in WDL...?
task qc_metrics {
  File controlBam
  File controlBamBai
  File tumorBam
  File tumorBamBai
  String outputDir = "."

  command {
    qc_and_metrics.pl ${outputDir} ${controlBam} ${tumorBam}
  }

  output {
    File qc_metrics = "${outputDir}/qc_metrics.json"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task ascat {
  File tumorBam
  File tumorBamBai
  File controlBam
  File controlBamBai
  File genomeFa
  File genomeFai
  File snpPosFile
  File snpLociFile
  File snpGcCorrectionsFile
  String SM
  String seqType
  String assembly
  String species
  String platform
  String gender = "L"
  String outputDir = "ascat"

  command {
    mkdir -p ${outputDir};

    ascat.pl \
    -r ${genomeFa} \
    -pr ${seqType} \
    -ra ${assembly} \
    -rs ${species} \
    -g ${gender} \
    -pl ${platform} \
    -s ${snpLociFile} \
    -sp ${snpPosFile} \
    -sg ${snpGcCorrectionsFile} \
    -o ${outputDir} \
    -t ${tumorBam} \
    -n ${controlBam} \
    -f ;
  }

  output {
    File abberationReliabilityPng = "${outputDir}/${SM}.aberrationreliability.png"
    File ASCATprofilePng = "${outputDir}/${SM}.ASCATprofile.png"
    File ASPCFPng = "${outputDir}/${SM}.ASPCF.png"
    File germlinePng = "${outputDir}/${SM}.germline.png"
    File rawProfilePng = "${outputDir}/${SM}.rawprofile.png"
    File sunrisePng = "${outputDir}/${SM}.sunrise.png"
    File tumorPng = "${outputDir}/${SM}.tumour.png"
    File copynumberCavemanCsv = "${outputDir}/${SM}.copynumber.caveman.csv"
    File copynumberCavemanVcf = "${outputDir}/${SM}.copynumber.caveman.vcf.gz"
    File copynumberCavemanVcfTbi = "${outputDir}/${SM}.copynumber.caveman.vcf.gz.tbi"
    File copynumberTxt = "${outputDir}/${SM}.copynumber.txt"
    File sampleStatistics = "${outputDir}/${SM}.samplestatistics.csv"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task pindel {
  File tumorBam
  File tumorBamBai
  String tumorBamId
  File controlBam
  File controlBamBai
  String controlBamId
  File genomeFa
  File genomeFai
  File simpleRepeatsFile
  File simpleRepeatsFileTbi
  File vcfFilterRulesFile
  File vcfFilterSoftRulesFile
  File codingGeneFootprintsFile
  File codingGeneFootprintsFileTbi
  File unmatchedNormalPanelGff3
  File unmatchedNormalPanelGff3Tbi
  File badAnchorLociFile
  File badAnchorLociFileTbi
  String seqType
  String assembly
  String species
  Int pindelInputThreads
  Int pindelNormalisedThreads
  String refExclude
  String outputDir = "pindel"

  command {
      mkdir -p ${outputDir};

      pindel.pl \
      -r ${genomeFa} \
      -e ${refExclude} \
      -st ${seqType} \
      -as ${assembly} \
      -sp ${species} \
      -s ${simpleRepeatsFile} \
      -f ${vcfFilterRulesFile} \
      -g ${codingGeneFootprintsFile} \
      -u ${unmatchedNormalPanelGff3} \
      -sf ${vcfFilterSoftRulesFile} \
      -b ${badAnchorLociFile} \
      -o ${outputDir} \
      -t ${tumorBam} \
      -n ${controlBam} \
      -c ${pindelInputThreads} \
      -l ${pindelNormalisedThreads} ;
  }

  output {
    File flaggedVcf = "${outputDir}/${tumorBamId}_vs_${controlBamId}.flagged.vcf.gz"
    File flaggedVcfTbi = "${outputDir}/${tumorBamId}_vs_${controlBamId}.flagged.vcf.gz.tbi"
    File germlineBed = "${outputDir}/${tumorBamId}_vs_${controlBamId}.germline.bed"
    File mt_bam = "${outputDir}/${tumorBamId}_vs_${controlBamId}_mt.bam"
    File mt_bam_bai = "${outputDir}/${tumorBamId}_vs_${controlBamId}_mt.bam.bai"
    File mt_bam_md5 = "${outputDir}/${tumorBamId}_vs_${controlBamId}_mt.bam.md5"
    File wt_bam = "${outputDir}/${tumorBamId}_vs_${controlBamId}_wt.bam"
    File wt_bam_bai = "${outputDir}/${tumorBamId}_vs_${controlBamId}_wt.bam.bai"
    File wt_bam_md5 = "${outputDir}/${tumorBamId}_vs_${controlBamId}_wt.bam.md5"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}


task brass {
  File tumorBam
  File tumorBamBai
  File tumorBamBas
  String tumorBamId
  File controlBam
  File controlBamBai
  File controlBamBas
  String controlBamId
  File genomeFa
  File genomeFai
  File ignoredRegionsFile
  File normalPanelGroupsFile
  File normalPanelGroupsFileTbi
  File genomeCacheFa
  File genomeCacheFai
  File genomeCacheFile
  File genomeCacheFileTbi
  File virusSeqsFile
  File microbeSeqsFilesPrefix
  Array[File] microbeSeqsFiles
  File bedCoordFile
  File cnPath
  File cnStats
  String seqType
  String assembly
  String species
  Int threads
  String refExclude
  String platform
  String outputDir = "brass"

  command {
    mkdir -p ${outputDir};

    # need to symlink bas files since brass assumes they are in
    # the same parent dir as the bam file
    ln -s ${tumorBamBas} `dirname ${tumorBam}`;
    ln -s ${controlBamBas} `dirname ${controlBam}`;

    brass.pl \
    -j 4 \
    -k 4 \
    -g ${genomeFa} \
    -e ${refExclude} \
    -pr ${seqType} \
    -as ${assembly} \
    -s ${species} \
    -pl ${platform} \
    -d ${ignoredRegionsFile} \
    -f ${normalPanelGroupsFile} \
    -g_cache ${genomeCacheFile} \
    -o ${outputDir} \
    -t ${tumorBam} \
    -n ${controlBam} \
    -vi ${virusSeqsFile} \
    -mi ${microbeSeqsFilesPrefix} \
    -b ${bedCoordFile} \
    -a ${cnPath} \
    -ss ${cnStats} \
    -c ${threads} \
    -l ${threads} ;
  }

  output {
    File controlBrmBam = "${outputDir}/${controlBamId}.brm.bam"
    File controlBrmBamBai = "${outputDir}/${controlBamId}.brm.bam.bai"
    File controlBrmBamMd5 = "${outputDir}/${controlBamId}.brm.bam.md5"
    File tumorBrmBam = "${outputDir}/${tumorBamId}.brm.bam"
    File tumorBrmBamBai = "${outputDir}/${tumorBamId}.brm.bam.bai"
    File tumorBrmBamMd5 = "${outputDir}/${tumorBamId}.brm.bam.md5"
    File annotBedPe = "${outputDir}/${tumorBamId}_vs_${controlBamId}.annot.bedpe"
    File annotVcf = "${outputDir}/${tumorBamId}_vs_${controlBamId}.annot.vcf.gz"
    File annotVcfTbi = "${outputDir}/${tumorBamId}_vs_${controlBamId}.annot.vcf.gz.tbi"
    File inversions = "${outputDir}/${tumorBamId}_vs_${controlBamId}.inversions.pdf"
    File diagnosticPlots = "${outputDir}/${tumorBamId}_vs_${controlBamId}.ngscn.diagnostic_plots.pdf"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}


task caveCnPrep {
  File cnPath
  String type

  command <<<
  if [[ "${type}" == "tumor" ]]; then
    export OFFSET=6 ;
  else
    export OFFSET=4 ;
  fi ;
  perl -ne '@F=(split q{,}, $_)[1,2,3,$OFFSET]; $F[1]-1; print join("\t",@F)."\n";' < ${cnPath} > ${type + ".cn.bed"};
  >>>

  output {
    File caveCnPrepOut = "${type}.cn.bed"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

task caveman {
  File tumorBam
  File tumorBamBai
  String tumorBamId
  File controlBam
  File controlBamBai
  String controlBamId
  File genomeFa
  File genomeFai
  File ascatContamFile
  File ignoredRegionsFile
  File tumorCopyNumberFile
  File controlCopyNumberFile
  File pindelGermlineMutsFile
  File flagBedFilesDir
  Array[File] flagBedFiles
  File unmatchedNormalFilesDir
  Array[File] unmatchedNormalFiles
  String seqType
  String assembly
  String species
  String seqProtocol
  Int threads
  String outputDir = "caveman"

  command {
    mkdir -p ${outputDir};

    caveman.pl \
    -ig ${ignoredRegionsFile} \
    -b ${flagBedFilesDir} \
    -np ${seqType} \
    -tp ${seqType} \
    -sa ${assembly} \
    -s ${species} \
    -st ${seqProtocol} \
    -o ${outputDir} \
    -tc ${tumorCopyNumberFile} \
    -nc ${controlCopyNumberFile} \
    -k ${ascatContamFile} \
    -tb ${tumorBam} \
    -nb ${controlBam} \
    -r ${genomeFai} \
    -u ${unmatchedNormalFilesDir} \
    -in ${pindelGermlineMutsFile} \
    -l ${threads} \
    -t ${threads} ;
  }

  output {
    File flaggedMutsVcf = "${outputDir}/${tumorBamId}_vs_${controlBamId}.flagged.muts.vcf.gz"
    File flaggedMutsVcfTbi = "${outputDir}/${tumorBamId}_vs_${controlBamId}.flagged.muts.vcf.gz.tbi"
    File mutsIdsVcf = "${outputDir}/${tumorBamId}_vs_${controlBamId}.muts.ids.vcf.gz"
    File mutsIdsVcfTbi = "${outputDir}/${tumorBamId}_vs_${controlBamId}.muts.ids.vcf.gz.tbi"
    File snpsIdsVcf = "${outputDir}/${tumorBamId}_vs_${controlBamId}.snps.ids.vcf.gz"
    File snpsIdsVcfTbi = "${outputDir}/${tumorBamId}_vs_${controlBamId}.snps.ids.vcf.gz.tbi"
    File noAnalysisBed = "${outputDir}/${tumorBamId}_vs_${controlBamId}.no_analysis.bed"
  }

  runtime {
    docker: "sanger-somatic-vc-workflow"
  }
}

workflow sanger_cgp_somatic_vc {
  File controlBam
  File controlBamBai
  File tumorBam
  File tumorBamBai
  File genomeFa
  File genomeFai
  String refExclude = "MT,GL%,hs37d5,NC_007605"
  String platform = "ILLUMINA"
  String seqType = "WGS"
  String seqProtocol = "genomic"
  String assembly = "GRCh37"
  String species = "human"
  String platform = "ILLUMINA"

  # bbAlleleCount
  Array[File] bbRefLociFiles

  # ASCAT
  File snpPosFile
  File snpLociFile
  File snpGcCorrectionsFile

  # PINDEL
  File simpleRepeatsFile
  File simpleRepeatsFileTbi
  File vcfFilterRulesFile
  File vcfFilterSoftRulesFile
  File codingGeneFootprintsFile
  File codingGeneFootprintsFileTbi
  File unmatchedNormalPanelGff3
  File unmatchedNormalPanelGff3Tbi
  File badAnchorLociFile
  File badAnchorLociFileTbi
  Int pindelInputThreads
  Int pindelNormalisedThreads

  # BRASS
  File ignoredRegionsFile
  File normalPanelGroupsFile
  File normalPanelGroupsFileTbi
  File genomeCacheFa
  File genomeCacheFai
  File genomeCacheFile
  File genomeCacheFileTbi
  File virusSeqsFile
  File microbeSeqsFilesPrefix
  Array[File] microbeSeqsFiles
  File bedCoordFile
  Int brassThreads

  # CAVEMAN
  File cavemanIgnoredRegionsFile
  File flagBedFilesDir
  Array[File] flagBedFiles
  File unmatchedNormalFilesDir
  Array[File] unmatchedNormalFiles
  Int cavemanThreads

  ##
  ## QC/Prep steps
  ##
  call getSampleId as tumor_sampleId {
    input: inBam = tumorBam
  }

  call getSampleId as control_sampleId {
    input: inBam = controlBam
  }

  call get_basename as tumor_basename {
    input: f = tumorBam
  }

  call get_basename as control_basename {
    input: f = controlBam
  }

  call compareGenotype {
    input: controlBam = controlBam,
           controlBamBai = controlBamBai,
           controlBamId = control_sampleId.SM,
           tumorBam = tumorBam,
           tumorBamBai = tumorBamBai,
           tumorBamId = tumor_sampleId.SM
  }

  call analyzeContamination as control_contam {
    input: process = "control",
           bamFile = controlBam,
           bamIndexFile = controlBamBai,
           SM = control_sampleId.SM
  }

  call analyzeContamination as tumor_contam {
    input: process = "tumor",
           bamFile = tumorBam,
           bamIndexFile = tumorBamBai,
           SM = tumor_sampleId.SM,
           ascatSegmentFile = ascat.copynumberCavemanCsv
  }

  call bam_stats as control_bam_stats {
    input: bamFile = controlBam,
           bamIndexFile = controlBamBai,
           bamFileName = control_basename.base
  }

  call bam_stats as tumor_bam_stats {
    input: bamFile = tumorBam,
           bamIndexFile = tumorBamBai,
           bamFileName = tumor_basename.base
  }

  scatter(chrLoci in bbRefLociFiles) {
    call get_basename {
      input: f = chrLoci
    }

    call bbAlleleCount as control_bbAlleleCount {
      input: bamFile = controlBam,
             bamIndexFile = controlBamBai,
             SM = control_sampleId.SM,
             bbRefLoci = chrLoci,
             bbRefName = get_basename.base
    }

    call bbAlleleCount as tumor_bbAlleleCount {
      input: bamFile = tumorBam,
             bamIndexFile = tumorBamBai,
             SM = tumor_sampleId.SM,
             bbRefLoci = chrLoci,
             bbRefName = get_basename.base
    }
  }

  # call qc_metrics {
  #   input: controlBam = controlBam,
  #          controlBamBai = controlBamBai,
  #          tumorBam = tumorBam,
  #          tumorBamBai = tumorBamBai
  # }


  ##
  ## ASCAT - copynumber analysis
  ##
  call ascat {
    input: controlBam = controlBam,
           controlBamBai = controlBamBai,
           tumorBam = tumorBam,
           tumorBamBai = tumorBamBai,
           SM = tumor_sampleId.SM,
           genomeFa = genomeFa,
           genomeFai = genomeFai,
           snpPosFile = snpPosFile,
           snpLociFile = snpLociFile,
           snpGcCorrectionsFile = snpGcCorrectionsFile,
           seqType = seqType,
           assembly = assembly,
	   platform = platform,
           species = species
  }


  ##
  ## Pindel - InDel calling
  ##
  call pindel {
    input: controlBam = controlBam,
           controlBamBai = controlBamBai,
           controlBamId = control_sampleId.SM,
           tumorBam = tumorBam,
           tumorBamBai = tumorBamBai,
           tumorBamId = tumor_sampleId.SM,
           genomeFa = genomeFa,
           genomeFai = genomeFai,
           simpleRepeatsFile = simpleRepeatsFile,
           simpleRepeatsFileTbi = simpleRepeatsFileTbi,
           vcfFilterRulesFile = vcfFilterRulesFile,
           vcfFilterSoftRulesFile = vcfFilterSoftRulesFile,
           codingGeneFootprintsFile = codingGeneFootprintsFile,
           codingGeneFootprintsFileTbi = codingGeneFootprintsFileTbi,
           unmatchedNormalPanelGff3 = unmatchedNormalPanelGff3,
           unmatchedNormalPanelGff3Tbi = unmatchedNormalPanelGff3Tbi,
           badAnchorLociFile = badAnchorLociFile,
           badAnchorLociFileTbi = badAnchorLociFileTbi,
           seqType = seqType,
           assembly = assembly,
           species = species,
	   refExclude = refExclude,
           pindelInputThreads = pindelInputThreads,
           pindelNormalisedThreads = pindelNormalisedThreads
  }


  ##
  ## BRASS - breakpoint analysis
  ##
  call brass {
    input: controlBam = controlBam,
           controlBamBai = controlBamBai,
           controlBamBas = control_bam_stats.basFile,
           controlBamId = control_sampleId.SM,
           tumorBam = tumorBam,
           tumorBamBai = tumorBamBai,
           tumorBamBas = tumor_bam_stats.basFile,
           tumorBamId = tumor_sampleId.SM,
           genomeFa = genomeFa,
           genomeFai = genomeFai,
           ignoredRegionsFile = ignoredRegionsFile,
           normalPanelGroupsFile = normalPanelGroupsFile,
           normalPanelGroupsFileTbi = normalPanelGroupsFileTbi,
           genomeCacheFa = genomeCacheFa,
           genomeCacheFai = genomeCacheFai,
           genomeCacheFile = genomeCacheFile,
           genomeCacheFileTbi = genomeCacheFileTbi,
           virusSeqsFile = virusSeqsFile,
           microbeSeqsFilesPrefix = microbeSeqsFilesPrefix,
           microbeSeqsFiles = microbeSeqsFiles,
           bedCoordFile = bedCoordFile,
           cnPath = ascat.copynumberCavemanCsv,
           cnStats = ascat.sampleStatistics,
           seqType = seqType,
           assembly = assembly,
           species = species,
	   platform = platform,
	   refExclude = refExclude,
           threads = brassThreads
  }


  ##
  ## Caveman - SNV analysis
  ##
  call caveCnPrep as control_caveCnPrep {
    input: type = "control",
           cnPath = ascat.copynumberCavemanCsv
  }

  call caveCnPrep as tumor_caveCnPrep {
    input: type = "tumor",
           cnPath = ascat.copynumberCavemanCsv
  }

  call caveman {
    input: controlBam = controlBam,
           controlBamBai = controlBamBai,
           controlBamId = control_sampleId.SM,
           tumorBam = tumorBam,
           tumorBamBai = tumorBamBai,
           tumorBamId = tumor_sampleId.SM,
           controlCopyNumberFile = control_caveCnPrep.caveCnPrepOut,
           tumorCopyNumberFile = tumor_caveCnPrep.caveCnPrepOut,
           genomeFa = genomeFa,
           genomeFai = genomeFai,
           ascatContamFile = ascat.sampleStatistics,
           ignoredRegionsFile = cavemanIgnoredRegionsFile,
           pindelGermlineMutsFile = pindel.germlineBed,
           flagBedFilesDir = flagBedFilesDir,
           flagBedFiles = flagBedFiles,
           unmatchedNormalFilesDir = unmatchedNormalFilesDir,
           unmatchedNormalFiles = unmatchedNormalFiles,
           seqType = seqType,
           assembly = assembly,
           species = species,
           seqProtocol = seqProtocol,
           threads = cavemanThreads
  }

  call httpimport.hello
}
