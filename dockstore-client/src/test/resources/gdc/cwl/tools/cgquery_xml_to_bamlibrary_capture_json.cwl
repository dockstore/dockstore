#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/cgquery_xml_to_bamlibrary_capture_json
  - class: InlineJavascriptRequirement
  - class: ShellCommandRequirement

class: CommandLineTool

inputs:
  []

outputs:
  - id: bam_libraryname_capturekey
    type: File
    outputBinding:
      glob: "bam_libraryname_capturekey.json"

  - id: tcga_multi_list
    type: File
    outputBinding:
      glob: "phs000178_wxs_illumina_live_all_states_multi_capture_per_library"

  - id: tcga_missing_list
    type: File
    outputBinding:
      glob: "phs000178_wxs_illumina_live_all_states_no_capture_per_library"

  - id: target_multi_list
    type: File
    outputBinding:
      glob: "phs000218_phs0004_phs000515_wxs_illumina_live_all_states_multi_capture_per_library"

  - id: target_missing_list
    type: File
    outputBinding:
      glob: "phs000218_phs0004_phs000515_wxs_illumina_live_all_states_no_capture_per_library"

baseCommand: ["tar", "xvf", "/cgquery_xml.tar.xz", "&&", "/usr/local/bin/cgquery_xml_to_bamlibrary_capture_json", "-t", "cgquery_xml/phs000178_wxs_illumina_live_all_states.xml", "-g", "cgquery_xml/phs000218_phs0004_phs000515_wxs_illumina_live_all_states.xml"]
