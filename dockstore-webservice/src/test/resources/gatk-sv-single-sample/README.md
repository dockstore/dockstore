#### This repository is deprecated. Please use the GATK-SV single sample pipeline WDL found in the https://github.com/broadinstitute/gatk-sv repository. 

# gatk-sv-single-sample

## Note

Except for this note which has been added to this README.md, this workflow is a snaphsot at commit
96aaa9622173bb2a37dfe6acf26669667c387c95 of https://github.com/broadinstitute/gatk-sv-single-sample, minus the scripts/ and terra_metadata/ directories.

This repository contains WDL scripts to run the GATK-SV Single Sample WGS Structural Variation detection pipeline.
  
Single Sample WGS SV Pipeline Overview  
Modules, Steps, and Workflows    

The workflow is divided into several modules shared with the joint calling version of this pipeline as well as several 
custom steps specific to single sample processing of a single sample with a reference panel.  

- Module 00: Preprocessing  
	- Step a: Run input SV algorithms and QC
	    - Manta
	    - Wham
	    - Coverage collection
	    - PE/SR evidence collection  
  	- Step b: Run binCov-dependent algorithms  
        - QC caller VCFs
    - Step c: Run cohort based callers (with reference panel)
        - Build and QC PE, SR, and BAF, RD matrices from case sample and reference panel 
        - Estimate ploidy of case sample
        - Run depth based callers: cn.MOPS, GATK gCNV
        - Compute median coverages, dosage scores	
	    - Standardize input matrices, VCFs & BED files  
  
- Module 01: Clustering  
	- Step 1: Run clustering
	    - PE/SR calls
	    - Depth calls  
	      
- Subset variants to those called in the case sample	      	      
- Split read testing and coordinate adjustment

- Module 04: Genotyping  
  
- Module 05_06: Batch Integration and cleanup  
