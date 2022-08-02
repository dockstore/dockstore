cwlVersion: v1.0
class: Workflow
inputs: []
outputs: []

steps:

  none_none:
    run: none.cwl
    in: []
    out: []
  none_hint:
    run: hint.cwl
    in: []
    out: []
  none_requirement:
    run: requirement.cwl
    in: []
    out: []

  hint_none:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: none.cwl
    in: []
    out: []
  hint_hint:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: hint.cwl
    in: []
    out: []
  hint_requirement:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: requirement.cwl
    in: []
    out: []

  requirement_none:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: none.cwl
    in: []
    out: []
  requirement_hint:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: hint.cwl
    in: []
    out: []
  requirement_requirement:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: requirement.cwl
    in: []
    out: []

