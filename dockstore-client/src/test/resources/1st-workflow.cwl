cwlVersion: v1.0
class: Workflow
inputs:
  - id: inp
    type: File
  - id: ex
    type: string

outputs:
  - id: classout
    type: File
    outputSource: "#compile/classfile"

steps:
  - id: untar
    run: tar-param.cwl
    in:
      - id: tarfile
        source: "#inp"
      - id: extractfile
        source: "#ex"
    out:
      - id: example_out

  - id: compile
    run: arguments.cwl
    in:
      - id: src
        source: "#untar/example_out"
    out:
      - id: classfile
