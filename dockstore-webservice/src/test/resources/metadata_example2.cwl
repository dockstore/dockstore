#!/usr/bin/env cwl-runner
cwlVersion: v1.0
class: CommandLineTool

label: An example tool demonstrating metadata.
doc: Note that this is an example and the metadata is not necessarily consistent.

inputs:
  aligned_sequences:
    type: File
    label: Aligned sequences in BAM format
    format: edam:format_2572
    inputBinding:
      position: 1

baseCommand: [ wc, -l ]

stdout: output.txt

outputs:
  report:
    type: stdout
    format: edam:format_1964
    label: A text file that contains a line count

s:author:
  - class: s:Person
    s:identifier: https://orcid.org/0000-0002-6130-1021
    s:email: mailto:dyuen@oicr.on.ca
    s:name: Denis Yuen

s:contributor:
  - class: s:Person
    s:identifier: http://orcid.org/0000-0002-7681-6415
    s:email: mailto:briandoconnor@gmail.com
    s:name: Brian O'Connor

s:citation: https://dx.doi.org/10.6084/m9.figshare.3115156.v2
s:codeRepository: https://github.com/common-workflow-language/common-workflow-language
s:dateCreated: "2016-12-13"
s:license: https://spdx.org/licenses/Apache-2.0

$namespaces:
  s: https://schema.org/
  edam: http://edamontology.org/

$schemas:
 - https://schema.org/docs/schema_org_rdfa.html
 - http://edamontology.org/EDAM_1.18.owl