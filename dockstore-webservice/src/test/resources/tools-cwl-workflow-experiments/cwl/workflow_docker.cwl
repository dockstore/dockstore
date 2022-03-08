cwlVersion: v1.0
class: Workflow

doc: |
   [![Docker Repository on Quay](https://quay.io/repository/ratschlab/workflow-experiment/status "Docker Repository on Quay")](https://quay.io/repository/ratschlab/workflow-experiment)
   Very simple and very artifical workflow to experiment with containers and cwl.

#dct:creator:
#  foaf:name: Marc Zimmermann
#  foaf:mbox: marc.zimmermann@inf.ethz.ch

inputs:
  input_file: File
  compute_exponent: int

outputs:
  cnt:
    type: File
    outputSource: count/counts

  res:
    type: File
    outputSource: compute/counts

steps:
  count:
    run: line_counts_docker.cwl
    in:
      input_file: input_file
    out: [counts]

  compute:
    run: complex_computation_docker.cwl
    in:
      input_file: count/counts
      exponent: compute_exponent
    out: [counts]
