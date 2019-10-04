# gatk-sv-clinical  

## Note

Except for this note which has been added to this README.md, this workflow is a snaphsot at commit
db89e89508bd562cd36e9c316ac968ab0a136c0e of https://github.com/wshands/gatk-sv-clinical, which is in turn a
fork of https://github.com/broadinstitute/gatk-sv-clinical.

This repository contains WDL scripts to run the GATK-SV Clinical WGS Structural Variation detection pipeline.
  
Clinical WGS SV Pipeline Overview  
Modules, Steps, and Workflows    

The workflow is divided into several modules shared with the joint calling version of this pipeline as well as several 
custom steps specific to clinical processing of a single sample with a reference panel.  

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
