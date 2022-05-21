cwlVersion: v1.0
class: Workflow
inputs:
outputs:

steps:

  none_none:
    run: none.cwl
  none_hint:
    run: hint.cwl
  none_requirement:
    run: requirement.cwl
  
  hint_none:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: none.cwl
  hint_hint:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: hint.cwl
  hint_requirement:
    hints:
      - class: DockerRequirement
        dockerPull: step
    run: requirement.cwl
    
  requirement_none:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: none.cwl
  requirement_hint:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: hint.cwl
  requirement_requirement:
    requirements:
      - class: DockerRequirement
        dockerPull: step
    run: requirement.cwl

