#!/usr/bin/env cwl-runner

class: CommandLineTool
id: "helloworld-checker"
label: "Hello world output validator"

cwlVersion: v1.0

$namespaces:
  dct: http://purl.org/dc/terms/
  foaf: http://xmlns.com/foaf/0.1/

dct:creator:
  "@id": "http://orcid.org/0000-0001-9758-0176"
  foaf:name: James Eddy
  foaf:mbox: "mailto:james.a.eddy@gmail.com"

requirements:
- class: DockerRequirement
  dockerPull: quay.io/ga4gh-dream/dockstore-tool-helloworld-checker:1.1.2

inputs:
  knowngood_file:
    type: File
    inputBinding:
      position: 1

  helloworld_file:
    type: File
    inputBinding:
      position: 2

outputs:
  results_file:
    type: File
    outputBinding:
      glob: "results.json"
  log_file:
    type: File
    outputBinding:
      glob: "log.txt"

baseCommand: ["python", "/usr/local/bin/helloworld_check"]
