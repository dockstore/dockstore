cwlVersion: v1.0.dev4
class: Workflow
inputs: []
outputs: []
id: a/workflow/id/that/contains/slashes

steps:

  step1:
    run:
      cwlVersion: "sbg:draft-2"
      class: CommandLineTool
      inputs: []
      outputs: []
      baseCommand: echo
      requirements:
        - class: DockerRequirement
          dockerPull: some_image
    in: []
    out: []

  step2:
    run:
      cwlVersion: "v1.0"
      class: CommandLineTool
      inputs: []
      outputs: []
      baseCommand: echo
      requirements:
        - class: DockerRequirement
          dockerPull: some_image
    in: []
    out: []

  step3:
    run:
      cwlVersion: "v1.2"
      class: CommandLineTool
      inputs: []
      outputs: []
      baseCommand: echo
      requirements:
        - class: DockerRequirement
          dockerPull: some_image
    in: []
    out: []

  step4:
    run:
      cwlVersion: "draft-3.dev5"
      class: CommandLineTool
      inputs: []
      outputs: []
      baseCommand: echo
      requirements:
        - class: DockerRequirement
          dockerPull: some_image
    in: []
    out: []
