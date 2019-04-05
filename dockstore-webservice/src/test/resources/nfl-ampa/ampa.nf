#!/bin/env nextflow

/*
 * Copyright (c) 2013-2018, Centre for Genomic Regulation (CRG) and the authors.
 *
 *   This file is part of 'AMPA-NF'.
 *
 *   Piper-NF is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Piper-NF is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with AMPA-NF.  If not, see <http://www.gnu.org/licenses/>.
 */


/* 
 * Authors: 
 * - Irantzu Anzar <iranmdl15@gmail.com>
 * - Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 *
 */


/*
 * Pipeline input params
 */

params.in = "$baseDir/example.fa" 	// input sequences
params.out = 'bigampa.data'		// result file
params.t = 0.225			// threshold value 
params.w = 7				// window size


/*
 * make sure the input file exists or exit
 */
fastaFile = file(params.in)
if( !fastaFile.exists() ) {
  exit 1, "The specified input file does not exist: ${params.in}"
}
log.info "Processing file: $fastaFile"


/*
 * split the input fasta in single sequences and execute a AMPA job for it
 */
Channel
        .fromPath(fastaFile)
        .splitFasta( record: [header:true, text: true] )
        .set { seq }

process ampa {
    //  defines the Input and Output
    input:
    set head, 'input.fa' from seq

    output:
    set head, stdout into ampaOut

    // The BASH script to be executed - for each - sequence
    """
    AMPA-BIGTABLE.pl -in=input.fa -noplot -rf=result -df=data -t=${params.t} -w=${params.w}
    cat result | grep '#' > /dev/stdout
    """

}


/*
 * Collecting AMPA result and saving to a file
 */
resultFile = file(params.out)
if( resultFile.exists() ) resultFile.delete()
log.info " --> Saving result to file: ${resultFile}"

ampaOut.map { head, str ->
        def id = getIDs(head)
        def val = getValues(str.trim())

        "${id[0]}\t${id[1]}\t${val[0]}\t${val[1]}\t${val[2]}\t${val[3]}\n"
    }
    .collectFile( name: resultFile, sort: 'none' )


/*
 * Given the sequence header retrieve the sequence ID
 */
def getIDs( line ) {

  def matcher = line =~ /^(\S+).+gene:(\S+).*/
  if( matcher.matches() ) {
    def seqId = matcher[0][1]
    def geneId = matcher[0][2]
    return [seqId, geneId]
  }
  else {
    println "Bad ID line: $line"
    return []
  }

}


/*
 *  return the values in the following order 
 *  - stretches 
 *  - protLen: 
 *  - ampLen: 
 *  - propensity
 */
def getValues(result) {

 def rm = result =~ /# This protein has (\d+) bactericidal stretches and it has (\d+) amino acids. AMP length: (\d+) Best AMP Propensity: ([0-9\.]+)/

  if( rm.matches() ) {
    return [rm[0][1], rm[0][2], rm[0][3], rm[0][4]]
  }
  else {
    println "Bad result line: $result"
    return []   
  }
}


