cwlVersion: v1.0
class: Workflow
inputs:
  inp: File
  ex: string

outputs:
  classout:
    outputSource: untar/example_out
    type:
      items:
      - File
      - 'null'
      type: array


steps:
  untar:
    run: tar-paramArrayed.cwl
    in:
      tarfile: inp
      extractfile: ex
    out: [example_out]
